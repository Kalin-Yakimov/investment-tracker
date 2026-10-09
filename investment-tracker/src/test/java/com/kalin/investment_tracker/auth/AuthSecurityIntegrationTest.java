package com.kalin.investment_tracker.auth;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.email.*;
import com.kalin.investment_tracker.role.Role;
import com.kalin.investment_tracker.role.RoleRepository;
import com.kalin.investment_tracker.security.*;
import com.kalin.investment_tracker.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({AuthenticationService.class, VerificationCodeService.class, VerificationCodeEncoder.class,
        AuthRateLimitService.class, AuthProtectionProperties.class, AuthSessionService.class,
        AuthSessionProperties.class, VerificationEmailListener.class, EmailDeliveryAuditService.class,
        AuthSecurityIntegrationTest.TestClock.class})
class AuthSecurityIntegrationTest {
    @TestConfiguration
    static class TestClock {
        @Bean Clock verificationClock() { return Clock.systemDefaultZone(); }
    }

    @Autowired AuthenticationService auth;
    @Autowired VerificationCodeService codes;
    @Autowired VerificationCodeEncoder encoder;
    @Autowired UserRepository users;
    @Autowired TokenRepository tokens;
    @Autowired RoleRepository roles;
    @Autowired AuthSessionService sessions;
    @Autowired AuthSessionRepository sessionRepository;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ActivityEventService activity;
    @MockitoBean PasswordEncoder passwords;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean EmailService mail;
    @MockitoBean(name = "verificationEmailExecutor") TaskExecutor executor;

    @BeforeEach
    void setUp() {
        when(passwords.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(executor).execute(any(Runnable.class));
    }

    @Test
    void activationIsSingleUseAndOldDuplicateValuesDoNotBreakLookup() {
        User user = user(false);
        token(user, "123456", TokenType.ACCOUNT_ACTIVATION, LocalDateTime.now().plusMinutes(15), true);
        token(user, "123456", TokenType.ACCOUNT_ACTIVATION, LocalDateTime.now().plusMinutes(15), false);
        auth.activateAccount(new ActivationRequest(user.getEmail(), "123456"));
        assertTrue(users.findById(user.getId()).orElseThrow().isEnabled());
        assertThrows(InvalidVerificationCodeException.class,
                () -> auth.activateAccount(new ActivationRequest(user.getEmail(), "123456")));
    }

    @Test
    void codeForOneEmailCannotActivateAnotherAccount() {
        User first = user(false);
        User second = user(false);
        token(first, "123456", TokenType.ACCOUNT_ACTIVATION, LocalDateTime.now().plusMinutes(15), false);
        token(second, "123457", TokenType.ACCOUNT_ACTIVATION, LocalDateTime.now().plusMinutes(15), false);
        assertThrows(InvalidVerificationCodeException.class,
                () -> auth.activateAccount(new ActivationRequest(second.getEmail(), "123456")));
        assertFalse(users.findById(second.getId()).orElseThrow().isEnabled());
        auth.activateAccount(new ActivationRequest(first.getEmail(), "123456"));
    }

    @Test
    void fifthWrongAttemptPermanentlyInvalidatesThatCodeWithoutLockingAccount() {
        User user = user(false);
        Token token = token(user, "123456", TokenType.ACCOUNT_ACTIVATION,
                LocalDateTime.now().plusMinutes(15), false);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(InvalidVerificationCodeException.class,
                    () -> auth.activateAccount(new ActivationRequest(user.getEmail(), "999999")));
        }
        assertNotNull(tokens.findById(token.getId()).orElseThrow().getValidatedAt());
        assertFalse(users.findById(user.getId()).orElseThrow().isAccountLocked());
        assertThrows(AuthRateLimitException.class,
                () -> auth.activateAccount(new ActivationRequest(user.getEmail(), "123456")));
    }

    @Test
    void expiredCodeIsInvalidatedAndDoesNotAutomaticallySendAnotherEmail() {
        User user = user(false);
        Token token = token(user, "123456", TokenType.ACCOUNT_ACTIVATION,
                LocalDateTime.now().minusSeconds(1), false);
        assertThrows(InvalidVerificationCodeException.class,
                () -> auth.activateAccount(new ActivationRequest(user.getEmail(), "123456")));
        assertNotNull(tokens.findById(token.getId()).orElseThrow().getValidatedAt());
        verifyNoInteractions(mail);
    }

    @Test
    void twoConcurrentRequestsCanConsumeCodeOnlyOnce() throws Exception {
        User user = user(false);
        token(user, "123456", TokenType.ACCOUNT_ACTIVATION, LocalDateTime.now().plusMinutes(15), false);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> activateAfter(start, user));
            var second = pool.submit(() -> activateAfter(start, user));
            start.countDown();
            int successful = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
        }
    }

    @Test
    void passwordResetConsumesCodeAndRevokesAllSessions() {
        User user = user(true);
        Token token = token(user, "123456", TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(15), false);
        var session = sessions.createSession(user, true, "Test", "127.0.0.1").session();
        auth.resetPassword(reset(user, "123456"));
        assertEquals("encoded:new-password", users.findById(user.getId()).orElseThrow().getPassword());
        assertNotNull(tokens.findById(token.getId()).orElseThrow().getValidatedAt());
        assertNotNull(sessionRepository.findById(session.getId()).orElseThrow().getRevokedAt());
        assertThrows(InvalidVerificationCodeException.class, () -> auth.resetPassword(reset(user, "123456")));
    }

    @Test
    void failureRollsBackPasswordCodeAndSessionRevocationTogether() {
        User user = user(true);
        Token token = token(user, "123456", TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(15), false);
        var session = sessions.createSession(user, true, "Test", "127.0.0.1").session();
        doThrow(new IllegalStateException("test audit failure")).when(activity)
                .recordSecurityEvent(any(), eq("PASSWORD_RESET"), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> auth.resetPassword(reset(user, "123456")));
        assertEquals("old-password-hash", users.findById(user.getId()).orElseThrow().getPassword());
        assertNull(tokens.findById(token.getId()).orElseThrow().getValidatedAt());
        assertNull(sessionRepository.findById(session.getId()).orElseThrow().getRevokedAt());
    }

    @Test
    void successfulCommitSendsHashedCodeAndSupersedesEarlierCodes() throws Exception {
        User user = user(false);
        Token previous = token(user, "123456", TokenType.ACCOUNT_ACTIVATION,
                LocalDateTime.now().plusMinutes(15), false);
        codes.issue(user, TokenType.ACCOUNT_ACTIVATION);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendEmail(eq(user.getEmail()), anyString(), eq(EmailTemplateName.ACTIVATE_ACCOUNT),
                anyString(), captor.capture(), anyString());
        assertTrue(captor.getValue().matches("[0-9]{6}"));
        Token latest = tokens.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(), TokenType.ACCOUNT_ACTIVATION).getFirst();
        assertNotEquals(captor.getValue(), latest.getToken());
        assertTrue(encoder.matches(captor.getValue(), latest.getToken(), user.getId(), TokenType.ACCOUNT_ACTIVATION));
        assertNotNull(tokens.findById(previous.getId()).orElseThrow().getValidatedAt());
    }

    @Test
    void rolledBackIssuanceDoesNotSendEmail() {
        User user = user(false);
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            codes.issue(user, TokenType.ACCOUNT_ACTIVATION);
            status.setRollbackOnly();
        });
        verifyNoInteractions(mail);
        assertTrue(tokens.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(), TokenType.ACCOUNT_ACTIVATION).isEmpty());
    }

    @Test
    void failedDeliveryKeepsCommittedCodeAndRecordsFailure() throws Exception {
        User user = user(false);
        doThrow(new jakarta.mail.MessagingException("test SMTP failure")).when(mail)
                .sendEmail(anyString(), anyString(), any(), anyString(), anyString(), anyString());
        codes.issue(user, TokenType.ACCOUNT_ACTIVATION);
        assertEquals(1, tokens.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(), TokenType.ACCOUNT_ACTIVATION).size());
        verify(activity).recordSecurityEvent(any(), eq("VERIFICATION_EMAIL_FAILED"), anyString(), isNull());
    }

    @Test
    void queueRejectionIsRecordedWithoutRollingBackCommittedCode() {
        User user = user(false);
        doThrow(new org.springframework.core.task.TaskRejectedException("test queue full"))
                .when(executor).execute(any(Runnable.class));
        codes.issue(user, TokenType.ACCOUNT_ACTIVATION);
        verify(activity).recordSecurityEvent(any(), eq("VERIFICATION_EMAIL_FAILED"), anyString(), isNull());
        assertEquals(1, tokens.findAllByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                user.getId(), TokenType.ACCOUNT_ACTIVATION).size());
    }

    @Test
    void unknownEmailGetsSameRecoveryResponseAndSendingBudget() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(UUID.randomUUID() + "@example.com").build();
        auth.forgotPassword(request);
        assertThrows(AuthRateLimitException.class, () -> auth.forgotPassword(request));
        verifyNoInteractions(mail);
    }

    @Test
    void passwordChangeInvalidatesPendingResetCodesAndSessions() {
        User user = user(true);
        Token pending = token(user, "123456", TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(15), false);
        var session = sessions.createSession(user, true, "Test", "127.0.0.1").session();
        when(passwords.matches("current-password", "old-password-hash")).thenReturn(true);
        auth.changePassword(ChangePasswordRequest.builder().currentPassword("current-password")
                .newPassword("new-password").confirmationPassword("new-password").build(), user);
        assertEquals("encoded:new-password", users.findById(user.getId()).orElseThrow().getPassword());
        assertNotNull(tokens.findById(pending.getId()).orElseThrow().getValidatedAt());
        assertNotNull(sessionRepository.findById(session.getId()).orElseThrow().getRevokedAt());
        assertThrows(InvalidVerificationCodeException.class, () -> auth.resetPassword(reset(user, "123456")));
    }

    @Test
    void registrationIsRolledBackIfVerificationCreationFails() throws Exception {
        String username = "registration-" + UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.com";
        roles.findByName("USER").orElseGet(() -> roles.save(Role.builder().name("USER").build()));
        doThrow(new IllegalStateException("test audit failure")).when(activity)
                .recordUserAction(any(), eq("ACCOUNT_REGISTERED"), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> auth.register(RegistrationRequest.builder()
                .username(username).email(email).password("password").build()));
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
        verifyNoInteractions(mail);
    }

    @Test
    void cleanupDeletesOnlyCodesPastRetention() {
        User user = user(false);
        Token old = token(user, "123456", TokenType.PASSWORD_RESET, LocalDateTime.now().minusDays(8), true);
        Token recent = token(user, "123457", TokenType.PASSWORD_RESET, LocalDateTime.now().minusHours(1), true);
        Token active = token(user, "123458", TokenType.PASSWORD_RESET, LocalDateTime.now().plusMinutes(15), false);
        codes.deleteOldExpiredCodes();
        assertFalse(tokens.existsById(old.getId()));
        assertTrue(tokens.existsById(recent.getId()));
        assertTrue(tokens.existsById(active.getId()));
    }

    private boolean activateAfter(CountDownLatch start, User user) throws InterruptedException {
        start.await();
        try {
            auth.activateAccount(new ActivationRequest(user.getEmail(), "123456"));
            return true;
        } catch (InvalidVerificationCodeException exception) {
            return false;
        }
    }

    private User user(boolean enabled) {
        Role role = roles.findByName("USER").orElseGet(() -> roles.save(Role.builder().name("USER").build()));
        return users.saveAndFlush(User.builder().username("test-" + UUID.randomUUID())
                .email(UUID.randomUUID() + "@example.com").password("old-password-hash")
                .enabled(enabled).accountLocked(false).roles(new ArrayList<>(List.of(role)))
                .items(new ArrayList<>()).createdDate(LocalDateTime.now()).build());
    }

    private Token token(User user, String code, TokenType type, LocalDateTime expiresAt, boolean used) {
        return tokens.saveAndFlush(Token.builder().user(user).token(code).type(type)
                .createdAt(LocalDateTime.now()).expiresAt(expiresAt)
                .validatedAt(used ? LocalDateTime.now() : null).build());
    }

    private ResetPasswordRequest reset(User user, String code) {
        return ResetPasswordRequest.builder().email(user.getEmail()).code(code).newPassword("new-password").build();
    }
}
