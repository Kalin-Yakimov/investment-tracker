package com.kalin.investment_tracker.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioSummaryCacheTest {
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
        Duration ttl = Duration.ofMinutes(15);
        SimpleCacheManager manager = (SimpleCacheManager) new CacheConfig().cacheManager(ttl, ttl, ttl, 1, 10);
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
