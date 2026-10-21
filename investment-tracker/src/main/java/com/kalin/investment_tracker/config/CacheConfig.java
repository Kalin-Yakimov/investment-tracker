package com.kalin.investment_tracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    public static final String CS_ITEMS_SEARCH_CACHE = "csItemsSearch";
    public static final String PORTFOLIO_SUMMARY_CACHE = "portfolioSummary";
    public static final String INVENTORY_CALCULATOR_CACHE = "inventoryCalculatorCache";
    public static final String STEAM_PROFILE_RESOLUTION_CACHE = "steamProfileResolution";

    @Bean
    public CacheManager cacheManager(CacheProperties settings) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new TransactionAwareCacheDecorator(buildCache(CS_ITEMS_SEARCH_CACHE, settings.getCsItemsSearch(), settings)),
                new TransactionAwareCacheDecorator(buildCache(PORTFOLIO_SUMMARY_CACHE, settings.getPortfolioSummary(), settings)),
                new CaffeineCache(INVENTORY_CALCULATOR_CACHE, Caffeine.newBuilder()
                        .initialCapacity(settings.getInitialCapacity()).maximumWeight(settings.getInventoryMaximumWeight())
                        .weigher((Object key, Object value) -> inventoryWeight(value))
                        .expireAfterWrite(settings.getInventoryCalculator().getTtl()).recordStats().build()),
                buildCache(STEAM_PROFILE_RESOLUTION_CACHE, settings.getSteamProfileResolution(), settings)
        ));
        return cacheManager;
    }

    private CaffeineCache buildCache(
            String name,
            CacheProperties.Policy policy,
            CacheProperties settings
    ) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .initialCapacity(settings.getInitialCapacity())
                .maximumSize(settings.maximum(policy))
                .expireAfterWrite(policy.getTtl())
                .recordStats()
                .build());
    }

    static int inventoryWeight(Object value) {
        if (value instanceof com.kalin.investment_tracker.inventory.SteamInventoryResponseDto inventory) {
            return 100 + (inventory.getItems() == null ? 0 : inventory.getItems().size());
        }
        return 100;
    }
}
