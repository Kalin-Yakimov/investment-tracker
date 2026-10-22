package com.kalin.investment_tracker.operations;

import com.kalin.investment_tracker.admin.SystemJobStatusService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionOperationsTest {
    @Test void freshnessGaugesArePureCacheReadsAndUnknownDatesAreNotInvented() {
        var feed = mock(com.kalin.investment_tracker.pricing.PriceFeedService.class);
        var now = java.time.Instant.parse("2026-10-22T10:00:00Z");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ProductionOperationsConfig().priceFreshnessMetrics(feed, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)).bindTo(registry);
            assertEquals(0, registry.get("investment.prices.publication.available").gauge().value());
            assertTrue(Double.isNaN(registry.get("investment.prices.publication.age.seconds").gauge().value()));
            when(feed.getCachedPublication()).thenReturn(new com.kalin.investment_tracker.pricing.PriceFeedSnapshot(
                    java.util.Map.of(), java.math.BigDecimal.ONE, "test-version", now.minusSeconds(7200), null, 0, now.minusSeconds(1000)));
            assertEquals(1, registry.get("investment.prices.publication.available").gauge().value());
            assertEquals(7200, registry.get("investment.prices.publication.age.seconds").gauge().value());
            assertEquals(1000, registry.get("investment.prices.fx.age.seconds").gauge().value());
            verify(feed, never()).refreshPriceCacheIfNeeded();
            verify(feed, never()).forceRefreshPriceCache();
        } finally { registry.close(); }
    }
    @Test void productionValidatesButNeverMigratesAndRefusesPendingSchemas() {
        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(mock(MigrationInfo.class));
        when(info.pending()).thenReturn(new MigrationInfo[0]);
        var strategy = new ProductionOperationsConfig().validateProductionMigrations();
        strategy.migrate(flyway);
        verify(flyway).validate(); verify(flyway, never()).migrate(); verify(flyway, never()).baseline();
        when(info.pending()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});
        assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));
        when(info.pending()).thenReturn(new MigrationInfo[0]); when(info.current()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> strategy.migrate(flyway));
    }

    @Test void metricsHaveOnlyFixedJobNamesAndTrackFailureWithoutBecomingLivenessChecks() {
        SystemJobStatusService jobs = new SystemJobStatusService();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            new ProductionOperationsConfig().backgroundJobMetrics(jobs).bindTo(registry);
            assertEquals(4, registry.getMeters().size());
            jobs.tryStart(SystemJobStatusService.PRICE_UPDATE, "test");
            assertEquals(1, registry.get("investment.jobs.running").tag("task", "priceUpdate").gauge().value());
            jobs.failed(SystemJobStatusService.PRICE_UPDATE, "test");
            assertEquals(1, registry.get("investment.jobs.failed").tag("task", "priceUpdate").gauge().value());
        } finally { registry.close(); }
    }

    @Test void correlationIsGeneratedNotReflectedAndMdcIsAlwaysRestored() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "attacker-controlled-value");
        var response = new MockHttpServletResponse();
        try {
            MDC.put("requestId", "previous");
            new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
                assertEquals(response.getHeader("X-Request-ID"), MDC.get("requestId"));
                assertNotEquals("attacker-controlled-value", MDC.get("requestId"));
            });
            assertEquals("previous", MDC.get("requestId"));
            java.util.UUID.fromString(response.getHeader("X-Request-ID"));
        } finally { MDC.clear(); }
        assertThrows(jakarta.servlet.ServletException.class, () -> new RequestCorrelationFilter().doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { throw new jakarta.servlet.ServletException("test"); }));
        assertNull(MDC.get("requestId"));
    }
}
