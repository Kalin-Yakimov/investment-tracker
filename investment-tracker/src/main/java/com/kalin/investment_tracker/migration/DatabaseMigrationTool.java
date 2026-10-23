package com.kalin.investment_tracker.migration;

import org.flywaydb.core.Flyway;

import java.net.URI;
import java.sql.DriverManager;

/** Explicit maintenance CLI: does not bootstrap Spring, background jobs, mail or market-data clients. */
public final class DatabaseMigrationTool {
    private DatabaseMigrationTool() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !java.util.Set.of("verify", "migrate", "adopt", "prepare-v1-reference").contains(args[0])) {
            throw new IllegalArgumentException("Usage: verify|migrate|adopt|prepare-v1-reference <confirmed-database-name>");
        }
        String url = required("MIGRATION_DB_URL");
        String username = required("MIGRATION_DB_USERNAME");
        String password = required("MIGRATION_DB_PASSWORD");
        if (!databaseName(url).equals(args[1])) throw new IllegalArgumentException("Database confirmation mismatch");
        Flyway flyway = configuredFlyway(url, username, password);
        if (args[0].equals("prepare-v1-reference")) {
            if (!args[1].startsWith("investment_reference_")) {
                throw new IllegalArgumentException("A disposable investment_reference_* database is required");
            }
            try (var connection = DriverManager.getConnection(url, username, password);
                 var query = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=?")) {
                query.setString(1, args[1]);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) != 0) throw new IllegalStateException("The V1 reference database must be empty");
                }
            }
            configuredV1Reference(url, username, password).migrate();
            return;
        }
        if (args[0].equals("migrate")) {
            flyway.migrate();
            flyway.validate();
            return;
        }
        String referenceUrl = required("MIGRATION_REFERENCE_URL");
        String referenceUser = System.getenv().getOrDefault("MIGRATION_REFERENCE_USERNAME", username);
        String referencePassword = System.getenv().getOrDefault("MIGRATION_REFERENCE_PASSWORD", password);
        if (databaseName(url).equals(databaseName(referenceUrl))) {
            throw new IllegalArgumentException("Reference must be a separate, disposable database");
        }
        // The reference must itself have successfully applied the shipped SQL, not just a baseline marker.
        Flyway reference = configuredV1Reference(referenceUrl, referenceUser, referencePassword);
        reference.validate();
        var current = reference.info().current();
        if (current == null || !"1".equals(current.getVersion().getVersion())
                || !"SQL".equals(current.getType().name())) {
            throw new IllegalStateException("Reference database must have executed V1 successfully");
        }
        try (var existing = DriverManager.getConnection(url, username, password);
             var expected = DriverManager.getConnection(referenceUrl, referenceUser, referencePassword)) {
            SchemaVerifier.verify(existing, expected);
        }
        if (args[0].equals("adopt")) {
            if (!"true".equals(System.getenv("MIGRATION_BACKUP_RESTORE_VERIFIED"))) {
                throw new IllegalStateException("A verified private backup/restore must be acknowledged before adoption");
            }
            if (flyway.info().current() != null) {
                throw new IllegalStateException("Database already has migration history; do not re-baseline it");
            }
            flyway.baseline();
            flyway.validate();
        }
        System.out.println("Schema verification succeeded. No application data was modified.");
    }

    public static Flyway configuredFlyway(String url, String username, String password) {
        return Flyway.configure().dataSource(url, username, password)
                .locations("classpath:db/migration/mysql").baselineVersion("1")
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .validateMigrationNaming(true).load();
    }

    public static Flyway configuredV1Reference(String url, String username, String password) {
        return Flyway.configure().configuration(configuredFlyway(url, username, password).getConfiguration())
                .target("1").load();
    }

    private static String databaseName(String url) {
        if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException("Explicit MySQL JDBC URL required");
        URI uri = URI.create(url.substring(5));
        if (uri.getUserInfo() != null || !safeOptions(uri.getQuery()) || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Use a MySQL URL with one database name, no credentials and only supported TLS/time-zone options");
        }
        return uri.getPath().substring(1);
    }

    private static boolean safeOptions(String query) {
        if (query == null) return true;
        return java.util.Arrays.stream(query.split("&", -1)).allMatch(option ->
                option.matches("sslMode=(VERIFY_IDENTITY|VERIFY_CA|REQUIRED)")
                        || option.matches("(connectionTimeZone|serverTimezone)=UTC"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
}
