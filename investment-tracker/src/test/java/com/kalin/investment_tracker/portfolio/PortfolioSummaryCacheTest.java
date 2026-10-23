package com.kalin.investment_tracker.portfolio;

import com.kalin.investment_tracker.config.CacheConfig;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioSummaryCacheTest {
    @org.junit.jupiter.api.Test
    void singleFlightSummaryLoadingCannotRepopulateAnEntryAfterAConcurrentEviction() throws Exception {
        Cache cache = cache();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var load = workers.submit(() -> cache.get(7, () -> {
                entered.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Missing test release");
                return "old summary";
            }));
            org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var evictionStarted = new java.util.concurrent.CountDownLatch(1);
            var invalidate = workers.submit(() -> {
                evictionStarted.countDown();
                transaction().executeWithoutResult(status -> cache.evict(7));
            });
            try {
                org.junit.jupiter.api.Assertions.assertTrue(evictionStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> invalidate.get(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            org.junit.jupiter.api.Assertions.assertEquals("old summary", load.get(5, java.util.concurrent.TimeUnit.SECONDS));
            invalidate.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.junit.jupiter.api.Assertions.assertNull(cache.get(7));
        org.junit.jupiter.api.Assertions.assertEquals("new summary", cache.get(7, () -> "new summary"));
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void summaryEvictionWaitsForCommitAndIsCancelledOnRollback(boolean commit) {
        Cache cache = cache();
        cache.put(7, "original summary");
        transaction().executeWithoutResult(status -> {
            cache.evict(7);
            assertEquals("original summary", cache.get(7, String.class));
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        if (commit) {
            assertNull(cache.get(7));
        } else {
            assertEquals("original summary", cache.get(7, String.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void summaryWritesArePublishedOnlyAfterSuccessfulCommit(boolean commit) {
        Cache cache = cache();
        transaction().executeWithoutResult(status -> {
            cache.put(7, "new summary");
            assertNull(cache.get(7));
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        if (commit) {
            assertEquals("new summary", cache.get(7, String.class));
        } else {
            assertNull(cache.get(7));
        }
    }

    private Cache cache() {
        var settings = new com.kalin.investment_tracker.config.CacheProperties();
        settings.setInitialCapacity(1); settings.setMaximumSize(10);
        SimpleCacheManager manager = (SimpleCacheManager) new CacheConfig().cacheManager(settings);
        manager.initializeCaches();
        Cache cache = manager.getCache(CacheConfig.PORTFOLIO_SUMMARY_CACHE);
        assertNotNull(cache);
        return cache;
    }

    private TransactionTemplate transaction() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:cache-" + UUID.randomUUID(), "sa", "");
        return new TransactionTemplate(new DataSourceTransactionManager(source));
    }
}
