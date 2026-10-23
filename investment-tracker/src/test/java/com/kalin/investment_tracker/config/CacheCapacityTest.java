package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.inventory.SteamInventoryItemDto;
import com.kalin.investment_tracker.inventory.SteamInventoryResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import jakarta.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;

class CacheCapacityTest {
    @Test void cacheBudgetsAreIndependentAndLargeInventoriesAreWeighted() {
        var settings = new CacheProperties(); settings.getCsItemsSearch().setMaximumSize(5L);
        settings.getPortfolioSummary().setMaximumSize(15L); settings.getSteamProfileResolution().setMaximumSize(25L);
        var caches = (SimpleCacheManager) new CacheConfig().cacheManager(settings); caches.initializeCaches();
        var searches = (CaffeineCache) ((TransactionAwareCacheDecorator)caches.getCache(CacheConfig.CS_ITEMS_SEARCH_CACHE)).getTargetCache();
        var summaries = (CaffeineCache) ((TransactionAwareCacheDecorator)caches.getCache(CacheConfig.PORTFOLIO_SUMMARY_CACHE)).getTargetCache();
        var profiles = (CaffeineCache)caches.getCache(CacheConfig.STEAM_PROFILE_RESOLUTION_CACHE);
        var inventories = (CaffeineCache)caches.getCache(CacheConfig.INVENTORY_CALCULATOR_CACHE);
        assertEquals(5, searches.getNativeCache().policy().eviction().orElseThrow().getMaximum());
        assertEquals(15, summaries.getNativeCache().policy().eviction().orElseThrow().getMaximum());
        assertEquals(25, profiles.getNativeCache().policy().eviction().orElseThrow().getMaximum());
        var big = SteamInventoryResponseDto.builder().items(java.util.Collections.nCopies(10000, SteamInventoryItemDto.builder().build())).build();
        for (int i = 0; i < 3; i++) inventories.put(i, big);
        inventories.getNativeCache().cleanUp();
        var eviction = inventories.getNativeCache().policy().eviction().orElseThrow();
        assertTrue(eviction.isWeighted()); assertTrue(eviction.weightedSize().orElseThrow() <= 20000);
        assertTrue(inventories.getNativeCache().estimatedSize() <= 1);
        assertEquals(10100, CacheConfig.inventoryWeight(big));
        assertEquals(100, CacheConfig.inventoryWeight(SteamInventoryResponseDto.builder().items(java.util.List.of()).build()));
    }
    @Test void unboundedCacheSizesAndInvalidTtlsAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var settings = new CacheProperties(); assertTrue(factory.getValidator().validate(settings).isEmpty());
            settings.getCsItemsSearch().setTtl(java.time.Duration.ZERO);
            assertFalse(factory.getValidator().validate(settings).isEmpty());
        }
    }
}
