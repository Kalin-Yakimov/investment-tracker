package com.kalin.investment_tracker.operations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSettingsValidatorTest {
    static final AtomicBoolean BEANS_CREATED = new AtomicBoolean();

    static MockEnvironment valid() throws Exception {
        MockEnvironment env = new MockEnvironment();
        for (String file : java.util.List.of("application.properties", "application-prod.properties")) {
            Properties properties = new Properties();
            try (var stream = ProductionSettingsValidatorTest.class.getClassLoader().getResourceAsStream(file)) { properties.load(stream); }
            properties.forEach((key, value) -> env.setProperty(key.toString(), value.toString()));
        }
        return env.withProperty("DB_URL", "jdbc:mysql://127.0.0.1:3306/investment")
                .withProperty("DB_USERNAME", "investment_app").withProperty("DB_PASSWORD", "synthetic-db-password")
                .withProperty("MAIL_USERNAME", "mail@example.invalid").withProperty("MAIL_PASSWORD", "synthetic-mail-password")
                .withProperty("JWT_SECRET", Base64.getEncoder().encodeToString("synthetic-unique-32-byte-test-key!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .withProperty("JWT_ISSUER", "test-issuer").withProperty("JWT_AUDIENCE", "test-audience")
                .withProperty("APP_FRONTEND_URL", "https://tracker.example.test")
                .withProperty("CORS_ALLOWED_ORIGINS", "https://tracker.example.test");
    }

    @Test void productionDefaultsAreSafeAndLocalSettingsAreNotValidated() throws Exception {
        assertDoesNotThrow(() -> ProductionSettingsValidator.validate(valid()));
        new ProductionSettingsValidator().postProcessEnvironment(new MockEnvironment(), null);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"dev,prod", "prod,dev"})
    void developmentAndProductionCannotBeCombinedRegardlessOfProfileOrder(String profiles) throws Exception {
        MockEnvironment env = valid();
        env.setActiveProfiles(profiles.split(","));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ProductionSettingsValidator().postProcessEnvironment(env, null));
        assertEquals("Unsafe or missing production setting: spring.profiles.active (dev with prod)", failure.getMessage());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "security.auth.cookie-secure|false", "security.auth.cookie-same-site|None",
            "spring.datasource.username|root", "spring.jpa.hibernate.ddl-auto|update",
            "spring.flyway.enabled|false", "spring.flyway.baseline-on-migrate|true",
            "spring.flyway.clean-disabled|false", "server.address|0.0.0.0",
            "management.server.address|0.0.0.0", "management.server.port|8080",
            "server.forward-headers-strategy|framework", "server.tomcat.remoteip.internal-proxies|.*",
            "server.tomcat.remoteip.trusted-proxies|.*", "app.bootstrap-admin-email|admin@example.invalid",
            "management.endpoints.web.exposure.include|health,prometheus,env",
            "management.endpoint.health.show-details|always", "springdoc.api-docs.enabled|true",
            "debug|true", "logging.level.org.hibernate.SQL|DEBUG", "spring.jpa.show-sql|true",
            "security.jwt.secret|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "security.jwt.access-expiration-minutes|1440", "security.auth.remembered-refresh-days|999",
            "security.auth.session-refresh-hours|999",
            "app.cors.allowed-origins|*", "app.frontend.base-url|http://localhost:3000"
    })
    void unsafeOverridesFailWithoutLeakingValues(String key, String value) throws Exception {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ProductionSettingsValidator.validate(valid().withProperty(key, value)));
        assertEquals("Unsafe or missing production setting: " + (key.equals("debug") ? "debug/trace" : key), error.getMessage());
        assertNull(error.getCause());
    }

    @ParameterizedTest
    @CsvSource({
            "jdbc:mysql://db.example.test/investment,false",
            "jdbc:mysql://db.example.test/investment?sslMode=REQUIRED,false",
            "jdbc:mysql://db.example.test/investment?sslMode=VERIFY_IDENTITY,true",
            "jdbc:mysql://127.0.0.1/investment,true",
            "jdbc:mysql://127.0.0.1/investment?allowPublicKeyRetrieval=true,false",
            "jdbc:mysql://user:private@127.0.0.1/investment,false",
            "jdbc:mysql://db.example.test/investment?sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC,true"
    })
    void databaseUrlsRequireVerifiedRemoteTlsAndNoCredentialOrUnsafeOptions(String url, boolean expected) {
        assertEquals(expected, ProductionSettingsValidator.safeDatabaseUrl(url));
    }

    @Test void factoryRegistrationRejectsUnsafeProductionBeforeAnyBeanIsCreated() {
        BEANS_CREATED.set(false);
        SpringApplication app = new SpringApplication(Probe.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app.run("--spring.profiles.active=prod", "--APP_FRONTEND_URL=https://tracker.example.test",
                        "--CORS_ALLOWED_ORIGINS=https://tracker.example.test", "--DB_URL=jdbc:mysql://127.0.0.1:1/never_connect",
                        "--DB_USERNAME=root", "--spring.main.banner-mode=off"));
        assertTrue(error.getMessage().contains("spring.datasource.username"));
        assertFalse(BEANS_CREATED.get());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class Probe {
        @Bean String mustNotInitialize() { BEANS_CREATED.set(true); return "never"; }
    }
}
