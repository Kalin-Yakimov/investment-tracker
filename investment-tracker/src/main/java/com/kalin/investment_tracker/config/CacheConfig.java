package com.kalin.investment_tracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
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
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(Arrays.asList(
                CS_ITEMS_SEARCH_CACHE,
                PRICE_HISTORY_CHART_CACHE,
                PORTFOLIO_SUMMARY_CACHE,
                TRACKED_ITEMS_CACHE,
                INVENTORY_CALCULATOR_CACHE
        ));

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(1000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats());

        return cacheManager;
    }
}
