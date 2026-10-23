package com.kalin.investment_tracker.mysql;

import com.kalin.investment_tracker.InvestmentTrackerApplication;
import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.email.EmailService;
import com.kalin.investment_tracker.migration.DatabaseMigrationTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.client.RestTemplate;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real prod profile, real HTTP listeners and restricted MySQL grants; synthetic data only. */
class MysqlProductionOperationsTest {
    @Test void productionStartsWithoutDdlAndManagementIsNotAvailableOnTheApplicationPort() throws Exception {
        String testUrl = System.getenv("MYSQL_TEST_URL");
        if (testUrl == null || !testUrl.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost|mysql):[0-9]+/investment_test_[a-z0-9_]+"))
            throw new IllegalStateException("An isolated MySQL test server is required");
        org.junit.jupiter.api.Assumptions.assumeTrue(!testUrl.startsWith("jdbc:mysql://mysql:"),
                "This local-proxy profile rehearsal needs loopback MySQL; remote production requires verified TLS");
        String root = System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root");
        String rootPassword = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String database = "investment_test_ops_" + suffix;
        String runtimeUser = "ops_" + suffix;
        String runtimePassword = "synthetic-operations-" + suffix;
        String url = testUrl.substring(0, testUrl.lastIndexOf('/') + 1) + database;
        try (var connection = DriverManager.getConnection(testUrl, root, rootPassword); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("CREATE USER '" + runtimeUser + "'@'%' IDENTIFIED BY '" + runtimePassword + "'");
            statement.execute("GRANT SELECT ON `" + database + "`.* TO '" + runtimeUser + "'@'%'");
        }
        var flyway = DatabaseMigrationTool.configuredFlyway(url, root, rootPassword);
        org.flywaydb.core.Flyway.configure().configuration(flyway.getConfiguration()).target("1").load().migrate();
        var runtimeFlyway = DatabaseMigrationTool.configuredFlyway(url, runtimeUser, runtimePassword);
        assertThrows(org.flywaydb.core.api.exception.FlywayValidateException.class,
                () -> new com.kalin.investment_tracker.operations.ProductionOperationsConfig().validateProductionMigrations().migrate(runtimeFlyway));
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        flyway.migrate();
        try (var connection = DriverManager.getConnection(url, root, rootPassword); var statement = connection.createStatement()) {
            statement.execute("GRANT SELECT ON `" + database + "`.* TO '" + runtimeUser + "'@'%'");
            var tables = new ArrayList<String>();
            try (var rows = statement.executeQuery("SHOW TABLES")) { while (rows.next()) tables.add(rows.getString(1)); }
            for (String table : tables) {
                if (!table.equals("flyway_schema_history") && table.matches("[A-Za-z0-9_]+"))
                    statement.execute("GRANT INSERT, UPDATE, DELETE ON `" + database + "`.`" + table + "` TO '" + runtimeUser + "'@'%'");
            }
        }
        int port = unusedPort(), managementPort = unusedPort();
        while (managementPort == port) managementPort = unusedPort();
        CsItemService catalogue = mock(CsItemService.class);
        EmailService mail = mock(EmailService.class);
        RestTemplate outbound = mock(RestTemplate.class);
        String[] args = {
                "--DB_URL=" + url, "--DB_USERNAME=" + runtimeUser, "--DB_PASSWORD=" + runtimePassword,
                "--MAIL_USERNAME=mail@example.invalid", "--MAIL_PASSWORD=synthetic-mail-password",
                "--JWT_SECRET=" + Base64.getEncoder().encodeToString("synthetic-unique-32-byte-test-key!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "--JWT_ISSUER=isolated-production-test", "--JWT_AUDIENCE=isolated-test-browser",
                "--APP_FRONTEND_URL=https://tracker.example.test", "--CORS_ALLOWED_ORIGINS=https://tracker.example.test",
                "--app.bootstrap-admin-email=", "--server.port=" + port, "--management.server.port=" + managementPort,
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                "--price.update.initial-delay-ms=86400000", "--price.history.prune-cron=-",
                "--security.auth.cleanup-cron=-", "--security.verification.cleanup-cron=-"
        };
        try (var context = new SpringApplicationBuilder(InvestmentTrackerApplication.class).profiles("prod")
                .initializers(ctx -> ctx.addBeanFactoryPostProcessor(factory -> {
                    BeanDefinitionRegistry definitions = (BeanDefinitionRegistry) factory;
                    definitions.removeBeanDefinition("csItemService"); factory.registerSingleton("csItemService", catalogue);
                    definitions.removeBeanDefinition("emailService"); factory.registerSingleton("emailService", mail);
                    definitions.removeBeanDefinition("restTemplate"); factory.registerSingleton("restTemplate", outbound);
                })).run(args);
             HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build()) {
            var health = get(client, managementPort, "/actuator/health/readiness");
            assertEquals(200, health.statusCode()); assertEquals("{\"status\":\"UP\"}", health.body());
            assertEquals(200, get(client, managementPort, "/actuator/health/liveness").statusCode());
            var metrics = get(client, managementPort, "/actuator/prometheus");
            assertEquals(200, metrics.statusCode()); assertTrue(metrics.body().contains("investment_jobs_failed"));
            assertTrue(metrics.body().contains("jvm_memory")); assertFalse(metrics.body().contains(runtimePassword));
            assertEquals(403, get(client, port, "/actuator/health").statusCode());
            assertEquals(403, get(client, managementPort, "/actuator/env").statusCode());
            assertEquals(403, get(client, managementPort, "/actuator/configprops").statusCode());
            assertEquals(403, get(client, managementPort, "/actuator/heapdump").statusCode());
            var api = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/validate-token"))
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", "198.51.100.42")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(401, api.statusCode()); assertTrue(api.headers().firstValue("strict-transport-security").isPresent());
            UUID.fromString(api.headers().firstValue("x-request-id").orElseThrow());
            var users = context.getBean(com.kalin.investment_tracker.user.UserRepository.class);
            var roles = context.getBean(com.kalin.investment_tracker.role.RoleRepository.class);
            var encoder = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
            var transactions = new org.springframework.transaction.support.TransactionTemplate(
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            transactions.executeWithoutResult(status -> users.saveAndFlush(com.kalin.investment_tracker.user.User.builder()
                    .username("production-test").email("production-test@example.invalid")
                    .password(encoder.encode("synthetic-test-password")).enabled(true).accountLocked(false)
                    .roles(new ArrayList<>(List.of(roles.findByName("USER").orElseThrow())))
                    .items(new ArrayList<>()).build()));
            var login = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/authenticate"))
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-For", "198.51.100.42")
                    .header("Origin", "https://tracker.example.test").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"production-test@example.invalid\",\"password\":\"synthetic-test-password\",\"rememberMe\":true}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, login.statusCode());
            String cookie = login.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(cookie.contains("HttpOnly") && cookie.contains("Secure") && cookie.contains("SameSite=Strict"));
            assertTrue(cookie.contains("Path=/api/v1/auth") && cookie.contains("Max-Age="));
            assertFalse(cookie.contains("Domain="));
            var blockedOrigin = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/refresh"))
                    .header("Origin", "https://untrusted.example.test").header("X-Requested-With", "XMLHttpRequest")
                    .header("Cookie", cookie.split(";", 2)[0]).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, blockedOrigin.statusCode());
            var logout = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/logout"))
                    .header("Origin", "https://tracker.example.test").header("X-Requested-With", "XMLHttpRequest")
                    .header("Cookie", cookie.split(";", 2)[0]).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(204, logout.statusCode());
            String clearedCookie = logout.headers().firstValue("set-cookie").orElseThrow();
            assertTrue(clearedCookie.contains("Max-Age=0") && clearedCookie.contains("Secure") && clearedCookie.contains("SameSite=Strict"));
            try (var connection = DriverManager.getConnection(url, runtimeUser, runtimePassword); var statement = connection.createStatement()) {
                assertThrows(java.sql.SQLException.class, () -> statement.execute("CREATE TABLE forbidden (id INT)"));
                assertThrows(java.sql.SQLException.class, () -> statement.execute("DELETE FROM flyway_schema_history"));
            }
            assertEquals("3", flyway.info().current().getVersion().getVersion());
            verifyNoInteractions(outbound, mail);
            // Only this test context's pool is closed; the shared disposable server is untouched.
            context.getBean(javax.sql.DataSource.class).unwrap(com.zaxxer.hikari.HikariDataSource.class).close();
            assertEquals(503, get(client, managementPort, "/actuator/health/readiness").statusCode());
            assertEquals(200, get(client, managementPort, "/actuator/health/liveness").statusCode());
        }
        // Database/user remain only on the disposable test server for diagnostic inspection.
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(java.time.Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static int unusedPort() throws Exception { try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); } }
}
