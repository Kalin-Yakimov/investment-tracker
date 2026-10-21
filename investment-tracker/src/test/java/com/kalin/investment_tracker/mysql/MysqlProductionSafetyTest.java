package com.kalin.investment_tracker.mysql;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.auth.*;
import com.kalin.investment_tracker.migration.DatabaseMigrationTool;
import com.kalin.investment_tracker.migration.SchemaVerifier;
import com.kalin.investment_tracker.role.Role;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.*;
import com.kalin.investment_tracker.user.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real InnoDB locks, real password authentication and the production SQL; no network providers. */
@DataJpaTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "security.auth.cleanup-cron=-", "security.verification.cleanup-cron=-"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthenticationService.class, AuthSessionService.class, AuthSessionProperties.class,
        VerificationCodeService.class, VerificationCodeEncoder.class, AuthRateLimitService.class,
        AuthProtectionProperties.class, UserDetailsServiceImpl.class, JwtService.class, JwtProperties.class,
        MysqlProductionSafetyTest.SecurityBeans.class})
class MysqlProductionSafetyTest {
    private static final String PASSWORD = "only-for-isolated-tests";
    private static final String URL = System.getenv("MYSQL_TEST_URL");
    private static final String USER = System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root");
    private static final String PASSWORD_DB = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost|mysql):[0-9]+/investment_test_[a-z0-9_]+")) {
            throw new IllegalStateException("Explicit isolated investment_test_* MySQL database required");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD_DB);
    }

    @TestConfiguration
    static class SecurityBeans {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }
        @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
            provider.setPasswordEncoder(passwords);
            return new ProviderManager(provider);
        }
    }

    @Autowired AuthenticationService auth;
    @Autowired AuthSessionService sessions;
    @Autowired AuthSessionRepository sessionRows;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TokenRepository tokens;
    @Autowired VerificationCodeEncoder codeEncoder;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway flyway;
    @MockitoBean ActivityEventService activity;

    @Test
    void emptyDatabaseWasMigratedAndValidatedByHibernateAndReexecutionDoesNotResetIds() {
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        User first = account();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        User second = account();
        assertNotEquals(first.getId(), second.getId());
        assertThrows(org.flywaydb.core.api.FlywayException.class, flyway::clean);
        assertTrue(users.existsById(first.getId()));
    }

    @Test
    void revocationWinsWhenRefreshWasAlreadyWaitingForTheAccountLock() throws Exception {
        User user = account();
        var original = sessions.createSession(user, true, "test", "127.0.0.1");
        race(user, () -> sessions.revokeAllForUser(user.getId()),
                () -> assertThrows(InvalidRefreshTokenException.class,
                        () -> sessions.rotateRefreshToken(original.rawToken())));
        assertFalse(sessions.isSessionActive(original.session().getId(), user.getId()));
    }

    @Test
    void successfulRefreshCannotEscapeAnOverlappingRevocation() throws Exception {
        User user = account();
        var original = sessions.createSession(user, true, "test", "127.0.0.1");
        race(user, () -> sessions.rotateRefreshToken(original.rawToken()),
                () -> sessions.revokeAllForUser(user.getId()));
        assertFalse(sessions.isSessionActive(original.session().getId(), user.getId()));
        assertThrows(InvalidRefreshTokenException.class, () -> sessions.rotateRefreshToken(original.rawToken()));
    }

    @Test
    void oldPasswordCannotCreateASessionAfterAnOverlappingReset() throws Exception {
        User user = account();
        Token code = tokens.saveAndFlush(Token.builder().user(user).type(TokenType.PASSWORD_RESET)
                .token(codeEncoder.encode("123456", user.getId(), TokenType.PASSWORD_RESET))
                .createdAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusMinutes(15)).build());
        assertNotNull(code.getId());
        ResetPasswordRequest reset = new ResetPasswordRequest();
        reset.setEmail(user.getEmail()); reset.setCode("123456"); reset.setNewPassword("new-test-password");
        race(user, () -> auth.resetPassword(reset),
                () -> assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                        () -> auth.authenticate(credentials(user, PASSWORD), "test", "127.0.0.1")));
        assertEquals(0, sessionRows.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), LocalDateTime.now()));
        var login = auth.authenticate(credentials(user, "new-test-password"), "test", "127.0.0.1");
        assertNotNull(login.response().getToken());
    }

    @Test
    void loginFinishingBeforePasswordResetHasItsNewSessionRevoked() throws Exception {
        User user = account();
        race(user, () -> auth.authenticate(credentials(user, PASSWORD), "test", "127.0.0.1"), () -> tx().executeWithoutResult(status -> {
            User locked = users.findLockedById(user.getId()).orElseThrow();
            locked.setPassword(passwords.encode("new-test-password"));
            sessions.revokeAllForUser(user.getId());
        }));
        assertEquals(0, sessionRows.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), LocalDateTime.now()));
    }

    @Test
    void lockingReloadsAnAccountAlreadyLoadedInThePersistenceContext() throws Exception {
        User user = account();
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> reader = executor.submit(() -> tx().executeWithoutResult(status -> {
                User stale = users.findById(user.getId()).orElseThrow();
                assertFalse(stale.isAccountLocked());
                read.countDown(); await(changed);
                assertTrue(users.findLockedById(user.getId()).orElseThrow().isAccountLocked());
            }));
            await(read);
            tx().executeWithoutResult(status -> users.findLockedById(user.getId()).orElseThrow().setAccountLocked(true));
            changed.countDown();
            reader.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void accountSecurityLockDoesNotLoadPortfolioRecords() {
        User user = account();
        tx().executeWithoutResult(status -> {
            User loaded = users.findById(user.getId()).orElseThrow();
            assertFalse(org.hibernate.Hibernate.isInitialized(loaded.getItems()));
            User locked = users.findLockedById(user.getId()).orElseThrow();
            assertFalse(org.hibernate.Hibernate.isInitialized(loaded.getItems()));
            assertTrue(entityManager.contains(locked));
            assertFalse(org.hibernate.Hibernate.isInitialized(locked.getItems()));
        });
    }

    @Test
    void accountLockReloadsRolesEvenAfterAnEarlierRepeatableReadSnapshot() throws Exception {
        assertRoleMembershipReload(false);
    }

    @Test
    void accountLockDoesNotRetainRolesRemovedAfterAnEarlierRepeatableReadSnapshot() throws Exception {
        assertRoleMembershipReload(true);
    }

    @Test
    void accountSecurityReloadPreservesPendingChangesInTheSameTransaction() {
        User user = account();
        tx().executeWithoutResult(status -> {
            User managed = users.findById(user.getId()).orElseThrow();
            managed.setFirstName("pending-change");
            User locked = users.findLockedById(user.getId()).orElseThrow();
            assertEquals("pending-change", locked.getFirstName());
            assertTrue(entityManager.contains(locked));
        });
        assertEquals("pending-change", users.findById(user.getId()).orElseThrow().getFirstName());
    }

    private void assertRoleMembershipReload(boolean initiallySupport) throws Exception {
        User user = account();
        Role support = tx().execute(status -> roles.findByName("SUPPORT")
                .orElseGet(() -> roles.saveAndFlush(Role.builder().name("SUPPORT").build())));
        if (initiallySupport) {
            tx().executeWithoutResult(status -> users.findLockedById(user.getId()).orElseThrow().getRoles().add(support));
        }
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch changed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> reader = executor.submit(() -> tx().executeWithoutResult(status -> {
                User stale = users.findById(user.getId()).orElseThrow();
                assertEquals(initiallySupport ? 2 : 1, stale.getRoles().size());
                read.countDown(); await(changed);
                User current = users.findLockedById(user.getId()).orElseThrow();
                assertEquals(!initiallySupport,
                        current.getRoles().stream().anyMatch(role -> role.getName().equals("SUPPORT")));
            }));
            await(read);
            tx().executeWithoutResult(status -> {
                User locked = users.findLockedById(user.getId()).orElseThrow();
                if (initiallySupport) locked.getRoles().removeIf(role -> role.getName().equals("SUPPORT"));
                else locked.getRoles().add(support);
            });
            changed.countDown();
            reader.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void lockingOneAccountDoesNotBlockAnotherAccountsLogin() throws Exception {
        User first = account();
        User second = account();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> holder = executor.submit(() -> tx().executeWithoutResult(status -> {
                users.findLockedById(first.getId()).orElseThrow(); locked.countDown(); await(release);
            }));
            await(locked);
            try {
                executor.submit(() -> auth.authenticate(credentials(second, PASSWORD), "test", "127.0.0.1"))
                        .get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            holder.get(15, TimeUnit.SECONDS);
        }
    }

    @Test
    void usernameOwnerChecksRespectRealCaseAndAccentInsensitiveMysqlCollation() {
        User owner = account();
        User other = account();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownName = "MiXeD-" + suffix;
        String occupiedName = "Älias-" + suffix;
        tx().executeWithoutResult(status -> users.findLockedById(owner.getId()).orElseThrow().setUsername(ownName));
        tx().executeWithoutResult(status -> users.findLockedById(other.getId()).orElseThrow().setUsername(occupiedName));
        var principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(owner, null);
        assertNotNull(auth.changeUsername(new ChangeUsernameRequest(ownName.toLowerCase(java.util.Locale.ROOT)), principal, "test-session").getToken());
        var conflict = assertThrows(AccountActionException.class, () -> auth.changeUsername(
                new ChangeUsernameRequest("alias-" + suffix), principal, "test-session"));
        assertEquals(com.kalin.investment_tracker.handler.BusinessErrorCodes.USERNAME_ALREADY_TAKEN, conflict.getErrorCode());
        assertEquals(owner.getEmail(), users.findById(owner.getId()).orElseThrow().getUsername());
        assertEquals(ownName.toLowerCase(java.util.Locale.ROOT), users.findById(owner.getId()).orElseThrow().fullName());
    }

    @Test
    void explicitAdoptionPreservesDataAndSequenceWhileAutomaticAdoptionIsRefused() throws Exception {
        String name = "investment_test_adopt_" + UUID.randomUUID().toString().replace("-", "");
        String legacyUrl = URL.substring(0, URL.lastIndexOf('/') + 1) + name;
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD_DB);
             var sql = connection.createStatement()) {
            sql.executeUpdate("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try (var connection = DriverManager.getConnection(legacyUrl, USER, PASSWORD_DB)) {
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/mysql/V1__initial_schema.sql"))
                    .populate(connection);
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("INSERT INTO _user(id,account_locked,enabled,created_date,email) VALUES(51,0,1,NOW(6),'test@example.invalid')");
                sql.executeUpdate("UPDATE _user_seq SET next_val=101");
            }
            Flyway legacy = DatabaseMigrationTool.configuredFlyway(legacyUrl, USER, PASSWORD_DB);
            assertThrows(org.flywaydb.core.api.FlywayException.class, legacy::migrate);
            try (var reference = DriverManager.getConnection(URL, USER, PASSWORD_DB)) {
                SchemaVerifier.verify(connection, reference);
            }
            connection.setReadOnly(false);
            legacy.baseline();
            assertEquals(0, legacy.migrate().migrationsExecuted);
            try (var sql = connection.createStatement(); var row = sql.executeQuery("SELECT id FROM _user")) {
                assertTrue(row.next()); assertEquals(51, row.getInt(1));
            }
            try (var sql = connection.createStatement()) { sql.executeUpdate("DROP INDEX uk_user_email ON _user"); }
            try (var reference = DriverManager.getConnection(URL, USER, PASSWORD_DB)) {
                assertThrows(IllegalStateException.class, () -> SchemaVerifier.verify(connection, reference));
            }
        }
        // Keep the disposable database for diagnostics; the harness stops its entire isolated server.
    }

    /** Hold operation A's account lock until B has been dispatched; verify B cannot complete early. */
    private void race(User user, Runnable first, Runnable second) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch dispatched = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> a = executor.submit(() -> tx().executeWithoutResult(status -> {
                users.findLockedById(user.getId()).orElseThrow(); first.run();
                locked.countDown(); await(release);
            }));
            await(locked);
            Future<?> b = executor.submit(() -> { dispatched.countDown(); second.run(); });
            try {
                await(dispatched);
                assertThrows(TimeoutException.class, () -> b.get(250, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactionManager); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrency coordination timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    private AuthenticationRequest credentials(User user, String password) {
        return AuthenticationRequest.builder().email(user.getEmail()).password(password).rememberMe(true).build();
    }
    private User account() {
        return tx().execute(status -> {
            Role role = roles.findByName("USER").orElseGet(() -> roles.saveAndFlush(Role.builder().name("USER").build()));
            String name = UUID.randomUUID().toString();
            return users.saveAndFlush(User.builder().username(name).email(name + "@example.invalid")
                    .password(passwords.encode(PASSWORD)).enabled(true).accountLocked(false)
                    .roles(new ArrayList<>(List.of(role))).items(new ArrayList<>()).build());
        });
    }
}
