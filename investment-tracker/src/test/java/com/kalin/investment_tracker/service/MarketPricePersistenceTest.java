package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.config.CacheConfig;
import com.kalin.investment_tracker.model.*;
import com.kalin.investment_tracker.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({MarketPricePersistenceService.class, CataloguePersistenceService.class, CacheConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MarketPricePersistenceTest {
    @Autowired MarketPricePersistenceService prices;
    @Autowired CataloguePersistenceService catalogueWriter;
    @Autowired ItemRepository items;
    @Autowired MarketPriceSnapshotRepository snapshots;
    @Autowired CsItemRepository catalogue;
    @Autowired CacheManager caches;
    @Autowired PlatformTransactionManager transactions;

    @Test void publicationUpdatesAllTrackingRecordsButStoresOnlyOneSharedPoint() {
        String name = UUID.randomUUID().toString();
        Item first = item(name, true), second = item(name, true), portfolio = item(name, false);
        PriceFeedSnapshot feed = feed(name, "publication-1");
        assertEquals(1, prices.persistFeed(feed));
        assertEquals(0, prices.persistFeed(feed));
        assertEquals(new BigDecimal("9.20"), stored(first).getPrice());
        assertEquals(stored(first).getPrice(), stored(second).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(portfolio).getPrice());
        assertEquals(1, history(name).size());
        assertEquals(0, prices.findRecordedPublication("publication-1").orElseThrow().rate().compareTo(new BigDecimal("0.92")));
    }

    @Test void priceOnlyUpdatePreservesPinNotificationAndOtherUserFields() {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        item.setShowInDashboard(true); item.setPriceNotification(new BigDecimal("20.00")); item.setQuantity(7);
        items.saveAndFlush(item);
        prices.persistFeed(feed(name, UUID.randomUUID().toString()));
        Item result = stored(item);
        assertTrue(result.getShowInDashboard()); assertEquals(7, result.getQuantity());
        assertEquals(new BigDecimal("20.00"), result.getPriceNotification()); assertTrue(result.getSteamFoundCheck());
    }

    @Test void selectedUpdateCannotModifyUnselectedOrPortfolioRecords() {
        String name = UUID.randomUUID().toString();
        Item selected = item(name, true), other = item(name, true), portfolio = item(name, false);
        assertEquals(1, prices.applySelected(feed(name, UUID.randomUUID().toString()), List.of(selected.getId(), portfolio.getId(), Long.MAX_VALUE)));
        assertEquals(new BigDecimal("9.20"), stored(selected).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(other).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(portfolio).getPrice());
    }

    @Test void dashboardUpdateUsesOnlyTrackedPinnedRecords() {
        String name = UUID.randomUUID().toString();
        Item pinned = item(name, true), unpinned = item(name, true), portfolio = item(name, false);
        pinned.setShowInDashboard(true); portfolio.setShowInDashboard(true);
        items.saveAllAndFlush(List.of(pinned, portfolio));
        prices.applyDashboard(feed(name, UUID.randomUUID().toString()));
        assertEquals(new BigDecimal("9.20"), stored(pinned).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(unpinned).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(portfolio).getPrice());
    }

    @Test void failedHistoryInsertRollsBackTheAlreadyExecutedPriceUpdate() {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        assertThrows(RuntimeException.class, () -> prices.persistFeed(feed(name, "x".repeat(129))));
        assertEquals(new BigDecimal("1.00"), stored(item).getPrice());
        assertTrue(history(name).isEmpty());
    }

    @Test void catalogueCommitClearsSearchCacheAndKeepsUserRelations() {
        String id = UUID.randomUUID().toString();
        CsItem entry = catalogue.saveAndFlush(entry(id, "old name"));
        Item item = item("related", false); item.setCatalogueItem(entry); items.saveAndFlush(item);
        var cache = caches.getCache(CacheConfig.CS_ITEMS_SEARCH_CACHE); assertNotNull(cache); cache.put("query", "old result");
        catalogueWriter.saveCatalogue(List.of(entry(id, "new name")), false);
        assertNull(cache.get("query"));
        assertEquals("new name", catalogue.findById(id).orElseThrow().getName());
        assertEquals(id, stored(item).getCatalogueItem().getId());
    }

    @Test void catalogueRollbackKeepsStoredEntriesAndSearchCache() {
        String id = UUID.randomUUID().toString(); catalogue.saveAndFlush(entry(id, "old name"));
        var cache = caches.getCache(CacheConfig.CS_ITEMS_SEARCH_CACHE); assertNotNull(cache); cache.put("query", "old result");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            catalogueWriter.saveCatalogue(List.of(entry(id, "new name")), false);
            assertEquals("old result", cache.get("query", String.class));
            status.setRollbackOnly();
        });
        assertEquals("old name", catalogue.findById(id).orElseThrow().getName());
        assertEquals("old result", cache.get("query", String.class));
    }

    @Test void incrementalCatalogueSaveAddsMissingItemsWithoutReplacingExistingOnes() {
        String existing = UUID.randomUUID().toString(), added = UUID.randomUUID().toString();
        catalogue.saveAndFlush(entry(existing, "stored name"));
        assertEquals(1, catalogueWriter.saveCatalogue(List.of(entry(existing, "replacement"), entry(added, "new")), true));
        assertEquals("stored name", catalogue.findById(existing).orElseThrow().getName());
        assertTrue(catalogue.existsById(added));
    }

    private Item item(String name, boolean tracked) {
        Item item = new Item(); item.setItemName(name); item.setMarketHashName(name);
        item.setSteamFoundCheck(tracked); item.setPrice(BigDecimal.ONE); item.setQuantity(1);
        return items.saveAndFlush(item);
    }
    private Item stored(Item item) { return items.findById(item.getId()).orElseThrow(); }
    private List<MarketPriceSnapshot> history(String name) {
        return snapshots.findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(name,
                MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, Instant.EPOCH);
    }
    private PriceFeedSnapshot feed(String name, String version) {
        return new PriceFeedSnapshot(Map.of(name, new BigDecimal("10.0000")), new BigDecimal("0.92000000"), version,
                Instant.parse("2026-10-10T08:00:00Z"), null, -1);
    }
    private CsItem entry(String id, String name) { return CsItem.builder().id(id).name(name).marketHashName(name).image("image").build(); }
}
