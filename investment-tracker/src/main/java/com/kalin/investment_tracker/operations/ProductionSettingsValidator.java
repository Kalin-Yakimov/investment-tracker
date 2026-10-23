package com.kalin.investment_tracker.operations;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/** Runs after config data, but before beans, connection pools or schema operations. */
public class ProductionSettingsValidator implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) return;
        require(!env.acceptsProfiles(Profiles.of("dev")), "spring.profiles.active (dev with prod)");
        validate(env);
    }

    static void validate(ConfigurableEnvironment env) {
        String frontend = value(env, "app.frontend.base-url");
        require(httpsOrigin(frontend), "app.frontend.base-url");
        require(frontend.equals(value(env, "app.cors.allowed-origins")), "app.cors.allowed-origins");
        require(safeDatabaseUrl(value(env, "spring.datasource.url")), "spring.datasource.url");
        String dbUser = value(env, "spring.datasource.username");
        require(!Set.of("root", "admin", "mysql").contains(dbUser.toLowerCase(Locale.ROOT)), "spring.datasource.username");
        secret(env, "spring.datasource.password", 12);
        secret(env, "spring.mail.password", 12);
        require(value(env, "spring.mail.username").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "spring.mail.username");
        require(strongJwt(value(env, "security.jwt.secret")), "security.jwt.secret");
        value(env, "security.jwt.issuer");
        value(env, "security.jwt.audience");
        require(number(env, "security.jwt.access-expiration-minutes") >= 1
                && number(env, "security.jwt.access-expiration-minutes") <= 30, "security.jwt.access-expiration-minutes");
        require(number(env, "security.auth.remembered-refresh-days") >= 1
                && number(env, "security.auth.remembered-refresh-days") <= 60, "security.auth.remembered-refresh-days");
        require(number(env, "security.auth.session-refresh-hours") >= 1
                && number(env, "security.auth.session-refresh-hours") <= 24, "security.auth.session-refresh-hours");
        equal(env, "security.auth.cookie-secure", "true");
        equal(env, "security.auth.cookie-same-site", "Strict");
        equal(env, "server.address", "127.0.0.1");
        equal(env, "management.server.address", "127.0.0.1");
        require(number(env, "server.port") > 0 && number(env, "management.server.port") > 0
                && number(env, "server.port") != number(env, "management.server.port"), "management.server.port");
        equal(env, "server.forward-headers-strategy", "native");
        equal(env, "server.tomcat.remoteip.internal-proxies", "127\\.0\\.0\\.1");
        equal(env, "server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For");
        equal(env, "server.tomcat.remoteip.protocol-header", "X-Forwarded-Proto");
        require(env.getProperty("server.tomcat.remoteip.trusted-proxies", "").isBlank(), "server.tomcat.remoteip.trusted-proxies");
        equal(env, "spring.jpa.hibernate.ddl-auto", "validate");
        equal(env, "spring.flyway.enabled", "true");
        equal(env, "spring.flyway.baseline-on-migrate", "false");
        equal(env, "spring.flyway.clean-disabled", "true");
        equal(env, "spring.flyway.validate-on-migrate", "true");
        equal(env, "springdoc.api-docs.enabled", "false");
        equal(env, "springdoc.swagger-ui.enabled", "false");
        equal(env, "spring.jpa.show-sql", "false");
        equal(env, "spring.mvc.log-request-details", "false");
        equal(env, "management.endpoints.access.default", "none");
        require(Set.copyOf(Arrays.asList(value(env, "management.endpoints.web.exposure.include").split(",")))
                .equals(Set.of("health", "prometheus")), "management.endpoints.web.exposure.include");
        equal(env, "management.endpoint.health.show-details", "never");
        equal(env, "management.endpoint.health.show-components", "never");
        equal(env, "spring.jmx.enabled", "false");
        equal(env, "spring.mail.properties.mail.smtp.starttls.required", "true");
        equal(env, "spring.mail.properties.mail.smtp.ssl.checkserveridentity", "true");
        require(env.getProperty("app.bootstrap-admin-email", "").isBlank(), "app.bootstrap-admin-email");
        require(!env.getProperty("debug", Boolean.class, false)
                && !env.getProperty("trace", Boolean.class, false), "debug/trace");
        for (var source : env.getPropertySources()) {
            if (source instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("logging.level.")) {
                        require(Set.of("INFO", "WARN", "ERROR", "OFF").contains(value(env, name).toUpperCase(Locale.ROOT)), name);
                    }
                }
            }
        }
    }

    static boolean httpsOrigin(String raw) {
        try {
            URI uri = URI.create(raw);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && !uri.getHost().equals("localhost")
                    && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty()) && !raw.contains("*");
        } catch (RuntimeException ignored) { return false; }
    }

    static boolean safeDatabaseUrl(String raw) {
        try {
            if (!raw.startsWith("jdbc:mysql://")) return false;
            URI uri = URI.create(raw.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !uri.getPath().matches("/[A-Za-z0-9_]+")) return false;
            Set<String> options = uri.getRawQuery() == null ? Set.of() : Set.copyOf(Arrays.asList(uri.getRawQuery().split("&", -1)));
            if (options.stream().anyMatch(option -> !option.matches("sslMode=VERIFY_IDENTITY|(connectionTimeZone|serverTimezone)=UTC"))) return false;
            return Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()) || options.contains("sslMode=VERIFY_IDENTITY");
        } catch (RuntimeException ignored) { return false; }
    }

    private static boolean strongJwt(String raw) {
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            return bytes.length >= 32 && java.util.stream.IntStream.range(0, bytes.length)
                    .map(index -> bytes[index] & 255).distinct().count() >= 8;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private static void secret(ConfigurableEnvironment env, String key, int minimum) {
        String raw = value(env, key);
        require(raw.length() >= minimum && !raw.toLowerCase(Locale.ROOT).startsWith("replace")
                && !raw.toLowerCase(Locale.ROOT).startsWith("change_me"), key);
    }
    private static String value(ConfigurableEnvironment env, String key) {
        try {
            String value = env.getProperty(key);
            require(value != null && !value.isBlank() && !value.contains("${"), key);
            return value;
        } catch (IllegalArgumentException exception) { throw invalid(key); }
    }
    private static int number(ConfigurableEnvironment env, String key) {
        try { return Integer.parseInt(value(env, key)); }
        catch (NumberFormatException exception) { throw invalid(key); }
    }
    private static void equal(ConfigurableEnvironment env, String key, String expected) { require(expected.equals(value(env, key)), key); }
    private static void require(boolean condition, String key) { if (!condition) throw invalid(key); }
    private static IllegalStateException invalid(String key) {
        // Never include an invalid supplied value or its parser exception in logs.
        return new IllegalStateException("Unsafe or missing production setting: " + key);
    }
}
