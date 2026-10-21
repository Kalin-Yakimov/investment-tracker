package com.kalin.investment_tracker.item;

import com.kalin.investment_tracker.tracking.TrackedItemService;
import com.kalin.investment_tracker.tracking.TrackedItemCreationService;
import com.kalin.investment_tracker.pricing.MarketPricePersistenceService;

import com.kalin.investment_tracker.pricing.PriceFeedService;

import com.kalin.investment_tracker.portfolio.PortfolioService;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.portfolio.PortfolioItemRequest;
import com.kalin.investment_tracker.tracking.TrackedItemUpdateRequest;
import com.kalin.investment_tracker.exception.ItemNotFoundException;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PortfolioService.class, TrackedItemService.class, TrackedItemCreationService.class, MarketPricePersistenceService.class,
        com.kalin.investment_tracker.validation.ResourceLimitsProperties.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ItemBoundaryIntegrationTest {
    @Autowired PortfolioService portfolio;
    @Autowired TrackedItemService tracked;
    @Autowired ItemRepository items;
    @Autowired CsItemRepository catalogue;
    @Autowired UserRepository users;
    @MockitoBean ActivityEventService activity;
    @MockitoBean PriceFeedService prices;
    @MockitoBean TaskExecutor applicationTaskExecutor;

    @Test
    void portfolioCannotReadEditOrDeleteOwnTrackedRecord() {
        User owner = user();
        Item item = item(owner, true);
        assertThrows(ItemNotFoundException.class, () -> portfolio.getItemById(item.getId(), owner));
        assertThrows(ItemNotFoundException.class, () -> portfolio.updateItem(item.getId(), request(), owner));
        assertThrows(ItemNotFoundException.class, () -> portfolio.deleteItem(item.getId(), owner));
        assertTrue(items.findById(item.getId()).orElseThrow().getSteamFoundCheck());
        verifyNoInteractions(activity);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void trackedOperationsCannotUseOwnPortfolioOrLegacyRecord(Boolean trackedFlag) {
        User owner = user();
        Item item = item(owner, trackedFlag);
        Long id = item.getId();
        assertThrows(ItemNotFoundException.class, () -> tracked.getTrackedItemDetails(id, owner));
        assertThrows(ItemNotFoundException.class, () -> tracked.getPriceHistory(id, owner));
        assertThrows(ItemNotFoundException.class, () -> tracked.updateTrackedItem(id, new TrackedItemUpdateRequest(true, null), owner));
        assertThrows(ItemNotFoundException.class, () -> tracked.toggleBookmark(id, owner));
        assertThrows(ItemNotFoundException.class, () -> tracked.deleteTrackedItem(id, owner));
        assertThrows(ItemNotFoundException.class, () -> tracked.requestPriceUpdate(List.of(id), owner));
        assertTrue(items.existsById(id));
        assertEquals(trackedFlag, items.findById(id).orElseThrow().getSteamFoundCheck());
        verifyNoInteractions(activity, prices, applicationTaskExecutor);
    }

    @Test
    void typeScopedQueriesAlsoRequireTheCorrectOwner() {
        User owner = user();
        User stranger = user();
        Item portfolioItem = item(owner, false);
        Item trackedItem = item(owner, true);
        assertTrue(items.findPortfolioItemByIdAndUserId(portfolioItem.getId(), stranger.getId()).isEmpty());
        assertTrue(items.findTrackedItemByIdAndUserId(trackedItem.getId(), stranger.getId()).isEmpty());
        assertThrows(ItemNotFoundException.class, () -> portfolio.updateItem(portfolioItem.getId(), request(), stranger));
        assertThrows(ItemNotFoundException.class, () -> portfolio.deleteItem(portfolioItem.getId(), stranger));
        assertThrows(ItemNotFoundException.class, () -> tracked.toggleBookmark(trackedItem.getId(), stranger));
        assertThrows(ItemNotFoundException.class, () -> tracked.deleteTrackedItem(trackedItem.getId(), stranger));
        assertThrows(ItemNotFoundException.class, () -> tracked.getPriceHistory(trackedItem.getId(), stranger));
        assertThrows(ItemNotFoundException.class, () -> tracked.requestPriceUpdate(List.of(trackedItem.getId()), stranger));
        verifyNoInteractions(activity, applicationTaskExecutor);
    }

    @Test
    void mixedPriceUpdateBatchIsRejectedWithoutPartiallyRunning() {
        User owner = user();
        Item trackedItem = item(owner, true);
        Item portfolioItem = item(owner, false);
        assertThrows(ItemNotFoundException.class,
                () -> tracked.requestPriceUpdate(List.of(trackedItem.getId(), portfolioItem.getId()), owner));
        verifyNoInteractions(activity, prices, applicationTaskExecutor);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void validPortfolioAndLegacyRecordsKeepWorking(Boolean trackedFlag) {
        User owner = user();
        Item item = item(owner, trackedFlag);
        assertEquals(item.getId(), portfolio.getItemById(item.getId(), owner).getId());
        portfolio.updateItem(item.getId(), request(), owner);
        assertEquals(new BigDecimal("2.00"), items.findById(item.getId()).orElseThrow().getPrice());
        assertEquals(trackedFlag, items.findById(item.getId()).orElseThrow().getSteamFoundCheck());
        portfolio.deleteItem(item.getId(), owner);
        assertFalse(items.existsById(item.getId()));
    }

    @Test
    void validTrackedRecordStillSupportsDetailsSettingsPinningAndRemoval() {
        User owner = user();
        Item item = item(owner, true);
        assertEquals(item.getId(), tracked.getTrackedItemDetails(item.getId(), owner).getId());
        tracked.updateTrackedItem(item.getId(), new TrackedItemUpdateRequest(true, new BigDecimal("10.00")), owner);
        assertTrue(items.findById(item.getId()).orElseThrow().getShowInDashboard());
        assertFalse(tracked.toggleBookmark(item.getId(), owner).bookmarked());
        assertEquals(1, tracked.getTrackedItems(owner, false, 0, 20).getTotalElements());
        tracked.deleteTrackedItem(item.getId(), owner);
        assertFalse(items.existsById(item.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "update", "delete"})
    void portfolioAndItsActivityRecordAreOneAtomicChange(String operation) {
        User owner = user();
        Item existing = item(owner, false);
        doThrow(new IllegalStateException("Simulated audit failure")).when(activity)
                .recordUserAction(any(User.class), anyString(), anyString(), isNull());
        assertThrows(IllegalStateException.class, () -> {
            switch (operation) {
                case "add" -> portfolio.addItem(request(), owner);
                case "update" -> portfolio.updateItem(existing.getId(), request(), owner);
                case "delete" -> portfolio.deleteItem(existing.getId(), owner);
                default -> throw new AssertionError("Unexpected operation");
            }
        });
        assertEquals(1, items.countAllUserItems(owner.getId()));
        assertEquals(new BigDecimal("1.00"), items.findById(existing.getId()).orElseThrow().getPrice());
    }

    @Test
    void catalogueImagesAreMappedInsideTransactionWithoutOpenSessionInView() {
        User owner = user();
        CsItem csItem = catalogue.saveAndFlush(CsItem.builder().id(UUID.randomUUID().toString())
                .name("case").marketHashName("case").image("image-url").build());
        Item item = item(owner, false);
        item.setCatalogueItem(csItem);
        items.saveAndFlush(item);
        assertEquals("image-url", portfolio.getItemById(item.getId(), owner).getImage());
        assertEquals("image-url", portfolio.getUserItems(owner, 0, 10, "newest_date").getContent().getFirst().getImage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"newest_date", "oldest_date", "bought", "highest_quantity", "highest_unit_price", "lowest_unit_price", "highest_total_price", "lowest_total_price"})
    void paginationHasStableTieBreakersAndExcludesTrackedRecords(String filter) {
        User owner = user();
        item(owner, false);
        item(owner, null);
        item(owner, true);
        var first = portfolio.getUserItems(owner, 0, 1, filter);
        var second = portfolio.getUserItems(owner, 1, 1, filter);
        assertEquals(2, first.getTotalElements());
        assertNotEquals(first.getContent().getFirst().getId(), second.getContent().getFirst().getId());
        assertEquals(first.getContent().getFirst().getId(), portfolio.getUserItems(owner, 0, 1, filter).getContent().getFirst().getId());
        assertEquals(2, portfolio.getItemHistory(owner, "case", 0, 10).getTotalElements());
    }

    @Test
    void invalidPaginationAndFiltersAreRejectedEvenForInternalServiceCalls() {
        User owner = user();
        assertThrows(IllegalArgumentException.class, () -> portfolio.getUserItems(owner, -1, 10, "newest_date"));
        assertThrows(IllegalArgumentException.class, () -> portfolio.getUserItems(owner, 0, 101, "newest_date"));
        assertThrows(IllegalArgumentException.class, () -> portfolio.getUserItems(owner, 0, 10, "unsupported"));
        assertThrows(IllegalArgumentException.class, () -> portfolio.getItemHistory(owner, "case", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> tracked.requestPriceUpdate(List.of(), owner));
        assertThrows(IllegalArgumentException.class, () -> tracked.requestPriceUpdate(null, owner));
        assertThrows(IllegalArgumentException.class, () -> tracked.requestPriceUpdate(java.util.Arrays.asList(1L, null), owner));
        assertThrows(IllegalArgumentException.class, () -> tracked.requestPriceUpdate(List.of(0L), owner));
        assertThrows(IllegalArgumentException.class, () -> tracked.requestPriceUpdate(java.util.Collections.nCopies(1001, 1L), owner));
        verifyNoInteractions(applicationTaskExecutor);
    }

    private User user() {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("not-used-in-this-test");
        user.setRoles(new ArrayList<>());
        user.setCreatedDate(LocalDateTime.now());
        return users.saveAndFlush(user);
    }

    private Item item(User owner, Boolean trackedFlag) {
        Item item = new Item();
        item.setUser(owner);
        item.setSteamFoundCheck(trackedFlag);
        item.setItemName("case");
        item.setMarketHashName("case");
        item.setItemStatus(ItemStatus.BOUGHT);
        item.setDate(LocalDate.of(2026, 10, 10));
        item.setQuantity(1);
        item.setPrice(BigDecimal.ONE);
        return items.saveAndFlush(item);
    }

    private PortfolioItemRequest request() {
        return new PortfolioItemRequest("case", null, ItemStatus.SOLD, LocalDate.of(2026, 10, 10), 2, new BigDecimal("2.00"));
    }
}
