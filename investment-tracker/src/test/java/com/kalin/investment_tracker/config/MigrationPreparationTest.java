package com.kalin.investment_tracker.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationPreparationTest {

    @Test
    void applicationUsesFlywayAndValidationWithoutAutomaticAdoption() throws IOException {
        Properties defaults = properties("application.properties");
        Properties production = properties("application-prod.properties");

        assertEquals("true", defaults.getProperty("spring.flyway.enabled"));
        assertEquals("false", defaults.getProperty("spring.flyway.baseline-on-migrate"));
        assertEquals("true", defaults.getProperty("spring.flyway.clean-disabled"));
        assertEquals("true", defaults.getProperty("spring.flyway.validate-on-migrate"));
        assertEquals("true", defaults.getProperty("spring.flyway.validate-migration-naming"));
        assertEquals("validate", defaults.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("validate", production.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("true", production.getProperty("spring.flyway.enabled"));
        assertEquals("true", properties("application-migrated.properties").getProperty("spring.flyway.enabled"));
    }

    @Test
    void testMigrationRunsOnceOnAnIsolatedEmptyDatabase() throws Exception {
        String url = testDatabaseUrl();
        Flyway flyway = testFlyway(url);

        assertEquals(1, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO migration_preparation_probe (id) VALUES (1)");
        }
    }

    @Test
    void existingTablesAreNotAutomaticallyAdoptedOrChanged() throws Exception {
        String url = testDatabaseUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE existing_probe (id INTEGER PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO existing_probe (id) VALUES (42)");
        }

        assertThrows(FlywayException.class, () -> testFlyway(url).migrate());

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT id FROM existing_probe")) {
            result.next();
            assertEquals(42, result.getInt(1));
        }
    }

    @Test
    void destructiveCleanOperationIsBlocked() throws Exception {
        String url = testDatabaseUrl();
        Flyway flyway = testFlyway(url);
        flyway.migrate();

        assertThrows(FlywayException.class, flyway::clean);
        assertEquals("1", flyway.info().current().getVersion().getVersion());
    }

    private static Flyway testFlyway(String url) throws IOException {
        Properties defaults = properties("application.properties");
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:migration-preparation")
                .baselineOnMigrate(Boolean.parseBoolean(defaults.getProperty("spring.flyway.baseline-on-migrate")))
                .cleanDisabled(Boolean.parseBoolean(defaults.getProperty("spring.flyway.clean-disabled")))
                .validateOnMigrate(Boolean.parseBoolean(defaults.getProperty("spring.flyway.validate-on-migrate")))
                .validateMigrationNaming(Boolean.parseBoolean(defaults.getProperty("spring.flyway.validate-migration-naming")))
                .load();
    }

    private static String testDatabaseUrl() {
        // No application URL or environment credentials are used in these tests.
        return "jdbc:h2:mem:migration-preparation-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static Properties properties(String resource) throws IOException {
        Properties properties = new Properties();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                MigrationPreparationTest.class.getClassLoader().getResourceAsStream(resource)
        ), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
