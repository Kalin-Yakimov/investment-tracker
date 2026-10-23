package com.kalin.investment_tracker.operations;

import com.kalin.investment_tracker.admin.SystemJobStatusService;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionOperationsConfig {
    private static final java.util.Set<String> OPERATIONS_PATHS = java.util.Set.of(
            "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/prometheus");
    @Bean
    public FlywayMigrationStrategy validateProductionMigrations() {
        return flyway -> {
            flyway.validate();
            if (flyway.info().pending().length != 0 || flyway.info().current() == null) {
                throw new IllegalStateException("Production schema is not prepared; run the separate migration procedure first");
            }
            // No migrate/baseline/repair: the runtime account must not need DDL.
        };
    }

    @Bean @Order(0)
    public SecurityFilterChain operationsSecurity(HttpSecurity http, Environment environment) throws Exception {
        int managementPort = environment.getRequiredProperty("management.server.port", Integer.class);
        return http.securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r
                        .requestMatchers(request -> request.getLocalPort() == managementPort
                                && "127.0.0.1".equals(request.getRemoteAddr())
                                && (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod()))
                                && OPERATIONS_PATHS.contains(request.getRequestURI())).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> response.setStatus(403))
                        .accessDeniedHandler((request, response, ex) -> response.setStatus(403)))
                .build();
    }

    @Bean
    public MeterBinder backgroundJobMetrics(SystemJobStatusService jobs) {
        return registry -> {
            for (String job : java.util.List.of(SystemJobStatusService.CATALOGUE_SYNC, SystemJobStatusService.PRICE_UPDATE)) {
                Gauge.builder("investment.jobs.failed", jobs, service -> "FAILED".equals(service.get(job).status()) ? 1 : 0)
                        .tag("task", job).register(registry);
                Gauge.builder("investment.jobs.running", jobs, service -> "RUNNING".equals(service.get(job).status()) ? 1 : 0)
                        .tag("task", job).register(registry);
            }
        };
    }

    @Bean
    public MeterBinder priceFreshnessMetrics(PriceFeedService feed, java.time.Clock clock) {
        return registry -> {
            Gauge.builder("investment.prices.publication.available", feed,
                    service -> service.getCachedPublication() == null ? 0 : 1).register(registry);
            Gauge.builder("investment.prices.publication.age.seconds", feed, service -> {
                var publication = service.getCachedPublication();
                return publication == null ? Double.NaN : Math.max(0, java.time.Duration.between(publication.observedAt(), clock.instant()).toSeconds());
            }).register(registry);
            Gauge.builder("investment.prices.fx.age.seconds", feed, service -> {
                var publication = service.getCachedPublication();
                return publication == null || publication.fxRateAsOf() == null ? Double.NaN
                        : Math.max(0, java.time.Duration.between(publication.fxRateAsOf(), clock.instant()).toSeconds());
            }).register(registry);
        };
    }

}
