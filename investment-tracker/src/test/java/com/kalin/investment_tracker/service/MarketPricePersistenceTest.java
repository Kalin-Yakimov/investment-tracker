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
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;

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

    @BeforeEach void resetPriceObservations() {
        snapshots.deleteAll();
    }

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

    @Test void laterSameDayPublicationReplacesRatherThanDuplicatesTheDailyRow() {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        PriceFeedSnapshot first = feed(name, "daily-first");
        prices.persistFeed(first);
        Long id = history(name).getFirst().getId();
        PriceFeedSnapshot later = publication(name, "daily-later", first.observedAt().plusSeconds(10801), "11.0000");
        assertEquals(1, prices.persistFeed(later));
        assertEquals(1, history(name).size());
        assertEquals(id, history(name).getFirst().getId());
        assertEquals(later.observedAt(), history(name).getFirst().getObservedAt());
        assertEquals(new BigDecimal("10.12"), stored(item).getPrice());
        assertEquals(0, prices.persistFeed(later));
    }

    @Test void unchangedPriceIsRecordedOnTheNextUtcPublicationDay() {
        String name = UUID.randomUUID().toString(); item(name, true);
        PriceFeedSnapshot first = publication(name, "utc-before", Instant.parse("2026-10-10T23:59:59Z"), "10.0000");
        PriceFeedSnapshot next = publication(name, "utc-after", Instant.parse("2026-10-11T00:00:00Z"), "10.0000");
        prices.persistFeed(first); prices.persistFeed(next);
        var history = history(name);
        assertEquals(2, history.size());
        assertEquals(history.getFirst().getPriceEur(), history.getLast().getPriceEur());
        assertEquals(LocalDate.of(2026, 10, 10), history.getFirst().getObservationDate());
        assertEquals(LocalDate.of(2026, 10, 11), history.getLast().getObservationDate());
    }

    @Test void olderPublicationCannotReplaceStoredPricesIncludingForANewTrackingRecord() {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        PriceFeedSnapshot newer = publication(name, "newer", Instant.parse("2026-10-11T08:00:00Z"), "12.0000");
        prices.persistFeed(newer);
        assertThrows(RuntimeException.class, () -> prices.persistFeed(feed(name, "older")));
        Item added = item(name, true);
        assertThrows(RuntimeException.class, () -> prices.applySelected(feed(name, "older"), List.of(added.getId())));
        assertEquals(1, history(name).size());
        assertEquals(new BigDecimal("11.04"), stored(item).getPrice());
        assertEquals(new BigDecimal("1.00"), stored(added).getPrice());
    }

    @Test void databaseRejectsASecondRowForTheSameItemSourceAndDay() {
        String name = UUID.randomUUID().toString(); item(name, true);
        prices.persistFeed(feed(name, "first-version"));
        assertThrows(RuntimeException.class, () -> snapshots.saveAndFlush(new com.kalin.investment_tracker.model.MarketPriceSnapshot(
                name, com.kalin.investment_tracker.model.MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, "another-version",
                Instant.parse("2026-10-10T09:00:00Z"), new BigDecimal("10"), new BigDecimal("0.92"), new BigDecimal("9.20"))));
        assertEquals(1, history(name).size());
    }

    @Test void failedDailyReplacementRollsBackBothHistoryAndCurrentPrice() {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        prices.persistFeed(feed(name, "valid-version"));
        PriceFeedSnapshot invalid = publication(name, "x".repeat(129), Instant.parse("2026-10-10T09:00:00Z"), "12.0000");
        assertThrows(RuntimeException.class, () -> prices.persistFeed(invalid));
        assertEquals("valid-version", history(name).getFirst().getSourceVersion());
        assertEquals(new BigDecimal("9.20"), stored(item).getPrice());
        assertEquals(new BigDecimal("9.20"), history(name).getFirst().getPriceEur());
    }

    @Test void concurrentReceiptOfTheSamePublicationStoresOnePoint() throws Exception {
        String name = UUID.randomUUID().toString(); item(name, true);
        PriceFeedSnapshot publication = feed(name, "concurrent-version");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> write = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return prices.persistFeed(publication); };
            Future<Integer> first = workers.submit(write), second = workers.submit(write);
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, history(name).size());
    }

    @Test void competingOlderAndNewerWritersAlwaysLeaveTheLatestDailyPrice() throws Exception {
        String name = UUID.randomUUID().toString(); Item item = item(name, true);
        PriceFeedSnapshot older = feed(name, "competing-older");
        PriceFeedSnapshot newer = publication(name, "competing-newer", older.observedAt().plusSeconds(3600), "12.0000");
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return prices.persistFeed(older); });
            Future<Integer> second = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return prices.persistFeed(newer); });
            start.countDown();
            assertEquals(1, second.get(10, TimeUnit.SECONDS));
            try { assertEquals(1, first.get(10, TimeUnit.SECONDS)); }
            catch (ExecutionException stale) {
                assertInstanceOf(com.kalin.investment_tracker.exeption.ExternalServiceException.class, stale.getCause());
            }
        }
        assertEquals(1, history(name).size());
        assertEquals(newer.observedAt(), history(name).getFirst().getObservedAt());
        assertEquals(new BigDecimal("11.04"), stored(item).getPrice());
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
        return publication(name, version, Instant.parse("2026-10-10T08:00:00Z"), "10.0000");
    }
    private PriceFeedSnapshot publication(String name, String version, Instant time, String usd) {
        return new PriceFeedSnapshot(Map.of(name, new BigDecimal(usd)), new BigDecimal("0.92000000"), version,
                time, null, -1);
    }
    private CsItem entry(String id, String name) { return CsItem.builder().id(id).name(name).marketHashName(name).image("image").build(); }
}
