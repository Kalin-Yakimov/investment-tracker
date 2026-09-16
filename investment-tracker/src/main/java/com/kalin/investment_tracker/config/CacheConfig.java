package com.kalin.investment_tracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CS_ITEMS_SEARCH_CACHE = "csItemsSearch";
    public static final String PRICE_HISTORY_CHART_CACHE = "priceHistoryChart";
    public static final String PORTFOLIO_SUMMARY_CACHE = "portfolioSummary";
    public static final String TRACKED_ITEMS_CACHE = "trackedItems";
    public static final String INVENTORY_CALCULATOR_CACHE = "inventoryCalculatorCache";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                buildCache(CS_ITEMS_SEARCH_CACHE, 12, TimeUnit.HOURS),
                buildCache(PRICE_HISTORY_CHART_CACHE, 15, TimeUnit.MINUTES),
                buildCache(PORTFOLIO_SUMMARY_CACHE, 15, TimeUnit.MINUTES),
                buildCache(TRACKED_ITEMS_CACHE, 15, TimeUnit.MINUTES),
                buildCache(INVENTORY_CALCULATOR_CACHE, 15, TimeUnit.MINUTES)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(duration, unit)
                .recordStats()
                .build());
    }
}
