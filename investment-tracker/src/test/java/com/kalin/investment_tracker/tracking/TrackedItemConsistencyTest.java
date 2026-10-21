package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.pricing.*;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({TrackedItemService.class, TrackedItemCreationService.class, MarketPricePersistenceService.class,
        com.kalin.investment_tracker.validation.ResourceLimitsProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TrackedItemConsistencyTest {
    private static final Instant PUBLICATION = Instant.parse("2026-10-20T08:00:00Z");
    @Autowired TrackedItemService tracked;
    @Autowired ItemRepository items;
    @Autowired UserRepository users;
    @Autowired CsItemRepository catalogue;
    @Autowired MarketPriceSnapshotRepository snapshots;
    @MockitoBean PriceFeedService prices;
    @MockitoBean ActivityEventService activity;
    @MockitoBean TaskExecutor applicationTaskExecutor;

    @BeforeEach void resetObservations() { snapshots.deleteAll(); }

    @Test void auditFailureRollsBackNewItemAndItsInitialSharedObservation() {
        User owner = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry, "valid-version"));
        doThrow(new IllegalStateException("audit unavailable")).when(activity)
                .recordUserAction(any(), anyString(), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> tracked.addTrackedItem(request(entry), owner));
        assertEquals(0, items.countTrackedItemsByUserId(owner.getId()));
        assertTrue(history(entry).isEmpty());
    }

    @Test void invalidSnapshotRollsBackWithoutCreatingAnItemOrAuditEvent() {
        User owner = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry, "x".repeat(129)));
        assertThrows(RuntimeException.class, () -> tracked.addTrackedItem(request(entry), owner));
        assertEquals(0, items.countTrackedItemsByUserId(owner.getId()));
        assertTrue(history(entry).isEmpty());
        verifyNoInteractions(activity);
    }

    @Test void concurrentAddsProduceExactlyOneTrackingRecordAndOneObservation() throws Exception {
        User owner = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry, "one-publication"));
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> add = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try { tracked.addTrackedItem(request(entry), owner); return true; }
                catch (DuplicateTrackedItemException expected) { return false; }
            };
            var first = workers.submit(add); var second = workers.submit(add); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, items.countTrackedItemsByUserId(owner.getId()));
        assertEquals(1, history(entry).size());
        verify(activity).recordUserAction(any(), eq("TRACKED_ITEM_ADDED"), anyString(), isNull());
    }

    @Test void repeatedPortfolioRowsAndDifferentTrackingOwnersRemainValid() {
        User owner = user(); User other = user(); CsItem entry = entry();
        when(prices.getCachedPublication()).thenReturn(feed(entry, "shared-publication"));
        for (int i = 0; i < 2; i++) {
            Item transaction = new Item(); transaction.setUser(owner); transaction.setCatalogueItem(entry);
            transaction.setItemName(entry.getName()); transaction.setMarketHashName(entry.getMarketHashName());
            transaction.setQuantity(1); transaction.setSteamFoundCheck(false); items.saveAndFlush(transaction);
        }
        tracked.addTrackedItem(request(entry), owner); tracked.addTrackedItem(request(entry), other);
        assertThrows(DuplicateTrackedItemException.class, () -> tracked.addTrackedItem(request(entry), owner));
        assertEquals(2, items.countAllUserItems(owner.getId()));
        assertEquals(1, items.countTrackedItemsByUserId(owner.getId()));
        assertEquals(1, items.countTrackedItemsByUserId(other.getId()));
        assertEquals(1, history(entry).size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cold", "older", "missing"})
    void initialPriceFallsBackToNewerStoredObservationWithoutBackdatingHistory(String state) {
        User owner = user(); CsItem entry = entry();
        snapshots.saveAndFlush(new MarketPriceSnapshot(entry.getMarketHashName(), MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                "newer-stored-" + entry.getId(), PUBLICATION.plusSeconds(3600), new BigDecimal("20"),
                new BigDecimal("0.92"), new BigDecimal("18.40")));
        if (state.equals("older")) when(prices.getCachedPublication()).thenReturn(feed(entry, "older-cached"));
        if (state.equals("missing")) when(prices.getCachedPublication()).thenReturn(new PriceFeedSnapshot(
                Map.of("another item", BigDecimal.ONE), new BigDecimal("0.92"), "missing-cached",
                PUBLICATION.plusSeconds(7200), null, -1));
        var added = tracked.addTrackedItem(request(entry), owner);
        assertEquals(new BigDecimal("18.40"), added.getPrice());
        assertEquals(new BigDecimal("18.40"), items.findById(added.getId()).orElseThrow().getPrice());
        assertEquals(1, history(entry).size());
        assertEquals(PUBLICATION.plusSeconds(3600), history(entry).getFirst().getObservedAt());
    }

    @Test void settingsAndPinningOnlyReadCacheAndDoNotRefreshOrChangeStoredPrice() {
        User owner = user(); CsItem entry = entry();
        long id = tracked.addTrackedItem(request(entry), owner).getId();
        clearInvocations(prices);
        tracked.updateTrackedItem(id, new TrackedItemUpdateRequest(true, new BigDecimal("12.00")), owner);
        assertFalse(tracked.toggleBookmark(id, owner).bookmarked());
        verify(prices, times(2)).getCachedItemPriceData(entry.getMarketHashName());
        verifyNoMoreInteractions(prices);
        Item stored = items.findById(id).orElseThrow();
        assertNull(stored.getPrice()); assertEquals(new BigDecimal("12.00"), stored.getPriceNotification());
    }

    @Test void twoConcurrentTogglesAreBothAppliedRatherThanLost() throws Exception {
        User owner = user(); CsItem entry = entry();
        long id = tracked.addTrackedItem(request(entry), owner).getId();
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> toggle = () -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return tracked.toggleBookmark(id, owner).bookmarked(); };
            var first = workers.submit(toggle); var second = workers.submit(toggle); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertFalse(items.findById(id).orElseThrow().getShowInDashboard());
    }

    private User user() {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.invalid");
        user.setPassword("not-used"); user.setRoles(new ArrayList<>()); user.setCreatedDate(LocalDateTime.now());
        return users.saveAndFlush(user);
    }
    private CsItem entry() {
        String name = UUID.randomUUID().toString();
        return catalogue.saveAndFlush(CsItem.builder().id(name).name(name).marketHashName(name).build());
    }
    private TrackedItemCreateRequest request(CsItem entry) { return new TrackedItemCreateRequest(null, null, entry.getId(), 1, false, null); }
    private PriceFeedSnapshot feed(CsItem entry, String version) {
        return new PriceFeedSnapshot(Map.of(entry.getMarketHashName(), new BigDecimal("10")), new BigDecimal("0.92"),
                version, PUBLICATION, null, -1);
    }
    private List<MarketPriceSnapshot> history(CsItem entry) {
        return snapshots.findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                entry.getMarketHashName(), MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, Instant.EPOCH);
    }
}
