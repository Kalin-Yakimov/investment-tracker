package com.kalin.investment_tracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CS_ITEMS_SEARCH_CACHE = "csItemsSearch";
    public static final String PORTFOLIO_SUMMARY_CACHE = "portfolioSummary";
    public static final String INVENTORY_CALCULATOR_CACHE = "inventoryCalculatorCache";
    public static final String STEAM_PROFILE_RESOLUTION_CACHE = "steamProfileResolution";

    @Bean
    public CacheManager cacheManager(
            @Value("${cache.cs-items-search.ttl:12h}") Duration csItemsSearchTtl,
            @Value("${cache.portfolio-summary.ttl:15m}") Duration portfolioSummaryTtl,
            @Value("${cache.inventory-calculator.ttl:15m}") Duration inventoryCalculatorTtl,
            @Value("${cache.initial-capacity:100}") int initialCapacity,
            @Value("${cache.maximum-size:1000}") long maximumSize
    ) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new TransactionAwareCacheDecorator(buildCache(CS_ITEMS_SEARCH_CACHE, csItemsSearchTtl, initialCapacity, maximumSize)),
                new TransactionAwareCacheDecorator(buildCache(PORTFOLIO_SUMMARY_CACHE, portfolioSummaryTtl, initialCapacity, maximumSize)),
                buildCache(INVENTORY_CALCULATOR_CACHE, inventoryCalculatorTtl, initialCapacity, maximumSize),
                buildCache(STEAM_PROFILE_RESOLUTION_CACHE, inventoryCalculatorTtl, initialCapacity, maximumSize)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(
            String name,
            Duration ttl,
            int initialCapacity,
            long maximumSize
    ) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}
