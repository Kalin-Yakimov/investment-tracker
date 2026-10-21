package com.kalin.investment_tracker.mysql;

import com.kalin.investment_tracker.catalogue.CsItemService;
import com.kalin.investment_tracker.email.EmailService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Complete application startup on a fresh or explicitly restored isolated MySQL database. */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
        "security.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "spring.mail.username=isolated@example.invalid", "spring.mail.password=test-only",
        "app.bootstrap-admin-email=", "price.update.initial-delay-ms=86400000",
        "price.history.prune-cron=-", "security.auth.cleanup-cron=-", "security.verification.cleanup-cron=-"
})
@AutoConfigureMockMvc
class MysqlMigrationStartupTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("MYSQL_TEST_URL");
        if (url == null || !url.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost|mysql):[0-9]+/investment_test_[a-z0-9_]+")) {
            throw new IllegalStateException("Explicit isolated investment_test_* MySQL database required");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
    }

    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @MockitoBean CsItemService catalogue;
    @MockitoBean EmailService mail;
    @MockitoBean RestTemplate outbound;

    @Test
    void migratedSchemaSupportsCompleteStartupAndSecurityWithoutProviderCalls() throws Exception {
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        flyway.validate();
        mvc.perform(get("/api/v1/auth/validate-token")).andExpect(status().isUnauthorized());
        verifyNoInteractions(outbound, mail);
    }
}
