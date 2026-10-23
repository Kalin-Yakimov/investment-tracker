package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.pricing.PriceFeedService;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.exception.ItemNotFoundException;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.pricing.PriceHistoryRepository;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.kalin.investment_tracker.pricing.MarketPricePersistenceService.RecordedMarketPrice;
import com.kalin.investment_tracker.pricing.PriceFreshnessDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class TrackedItemServiceTest {

    @Mock private PriceFeedService priceFeedService;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private CsItemRepository csItemRepository;
    @Mock private TaskExecutor applicationTaskExecutor;
    @Mock private ActivityEventService activityEventService;
    @Mock private TrackedItemCreationService creationService;
    @Mock private com.kalin.investment_tracker.pricing.MarketPricePersistenceService marketPrices;

    @InjectMocks
    private TrackedItemService service;

    private User user;
    private Item trackedItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7);

        trackedItem = new Item();
        trackedItem.setId(42L);
        trackedItem.setItemName("AK-47 | Redline (Field-Tested)");
        trackedItem.setMarketHashName("AK-47 | Redline (Field-Tested)");
        trackedItem.setCatalogueItem(CsItem.builder()
                .id("skin-42")
                .image("image-url")
                .build());
        trackedItem.setQuantity(1);
        trackedItem.setPrice(new BigDecimal("45.00"));
        trackedItem.setSteamFoundCheck(true);
        trackedItem.setUser(user);
    }

    @Test
    void queuedUpdatesAreLimitedToOnePerAccountAndReleasedAfterExecution() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(42L), 7)).thenReturn(List.of(trackedItem));
        service.requestPriceUpdate(List.of(42L), user);
        assertThrows(IllegalArgumentException.class, () -> service.requestPriceUpdate(List.of(42L), user));
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(applicationTaskExecutor).execute(task.capture());
        task.getValue().run();
        service.requestPriceUpdate(List.of(42L), user);
    }

    @Test
    void queueRejectionReleasesAccountForALaterAttempt() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(42L), 7)).thenReturn(List.of(trackedItem));
        doThrow(new TaskRejectedException("queue full")).doNothing().when(applicationTaskExecutor).execute(any());
        assertThrows(ExternalServiceException.class, () -> service.requestPriceUpdate(List.of(42L), user));
        service.requestPriceUpdate(List.of(42L), user);
    }

    @Test
    void failedBackgroundUpdateAlsoReleasesAccount() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(42L), 7)).thenReturn(List.of(trackedItem));
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(applicationTaskExecutor).execute(any());
        doThrow(new IllegalStateException()).when(priceFeedService).updatePricesForItems(List.of(42L));
        service.requestPriceUpdate(List.of(42L), user);
        service.requestPriceUpdate(List.of(42L), user);
    }

    @Test
    void trackedItemsAreAlwaysLoadedForAuthenticatedUser() {
        stubTrackedPage();

        var result = service.getTrackedItems(user, false, 0, 20).getContent();

        assertEquals(1, result.size());
        verify(priceFeedService).refreshPriceCacheIfNeeded();
        verify(priceFeedService, never()).getItemPriceData(any());
        verify(itemRepository, org.mockito.Mockito.times(2)).findTrackedPage(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(false), any());
        verify(itemRepository, never()).findAllSteamTrackedItems();
    }

    @Test
    void currentFeedPriceOverridesStoredDisplayPrice() {
        stubTrackedPage();
        when(priceFeedService.getCachedItemPriceData(trackedItem.getMarketHashName()))
                .thenReturn(new PriceFeedService.ItemPriceData(new BigDecimal("52.75")));

        var result = service.getTrackedItems(user, false, 0, 20).getContent();

        assertEquals(new BigDecimal("52.75"), result.getFirst().getPrice());
        assertEquals(new BigDecimal("52.75"), result.getFirst().getPrice24h());
    }

    @Test
    void newerPersistedPublicationWinsOverOlderLocalCacheWithoutInventingARecentCheck() {
        var newer = Instant.parse("2026-10-20T12:00:00Z");
        var stored = new RecordedMarketPrice(trackedItem.getPrice(), newer, newer, newer);
        var fallback = new PriceFreshnessDto("STORED_FALLBACK", "CSGOTRADER_24H", newer, newer,
                null, newer, true, false, false);
        stubTrackedPage();
        when(marketPrices.findLatestPrices(List.of(trackedItem.getMarketHashName())))
                .thenReturn(Map.of(trackedItem.getMarketHashName(), stored));
        when(priceFeedService.getCachedItemPriceData(trackedItem.getMarketHashName()))
                .thenReturn(cachedPrice("40.00", newer.minusSeconds(86400)));
        when(priceFeedService.describeStoredPrice(stored))
                .thenReturn(new PriceFeedService.ItemPriceData(stored.price(), fallback));

        var result = service.getTrackedItems(user, false, 0, 20).getContent().getFirst();

        assertEquals(new BigDecimal("45.00"), result.getPrice());
        assertEquals(fallback, result.getPriceFreshness());
        org.junit.jupiter.api.Assertions.assertNull(result.getPriceFreshness().lastSuccessfulCheck());
        verify(marketPrices).findLatestPrices(List.of(trackedItem.getMarketHashName()));
    }

    @Test
    void mutationDoesNotAttachOldSnapshotMetadataToTheCurrentLockedPriceOrPerformNetworkRefresh() {
        var oldDate = Instant.parse("2026-10-19T12:00:00Z");
        var stored = new RecordedMarketPrice(new BigDecimal("40.00"), oldDate, oldDate, oldDate);
        when(itemRepository.findLockedTrackedItem(42L, 7)).thenReturn(Optional.of(trackedItem));
        when(itemRepository.save(trackedItem)).thenReturn(trackedItem);
        when(marketPrices.findLatestPrices(List.of(trackedItem.getMarketHashName())))
                .thenReturn(Map.of(trackedItem.getMarketHashName(), stored));
        when(priceFeedService.getCachedItemPriceData(trackedItem.getMarketHashName()))
                .thenReturn(cachedPrice("40.00", oldDate));

        var result = service.toggleBookmark(42L, user).item();

        assertEquals(new BigDecimal("45.00"), result.getPrice());
        assertEquals("STORED_FALLBACK", result.getPriceFreshness().status());
        org.junit.jupiter.api.Assertions.assertNull(result.getPriceFreshness().observedAt());
        verify(priceFeedService, never()).refreshPriceCacheIfNeeded();
        verify(priceFeedService, never()).describeStoredPrice(any());
    }

    @Test
    void newerCachedPublicationMayDescribeTheLatestPriceAfterMutation() {
        var oldDate = Instant.parse("2026-10-19T12:00:00Z");
        var stored = new RecordedMarketPrice(new BigDecimal("40.00"), oldDate, oldDate, oldDate);
        var current = cachedPrice("45.00", oldDate.plusSeconds(86400));
        when(itemRepository.findLockedTrackedItem(42L, 7)).thenReturn(Optional.of(trackedItem));
        when(itemRepository.save(trackedItem)).thenReturn(trackedItem);
        when(marketPrices.findLatestPrices(List.of(trackedItem.getMarketHashName())))
                .thenReturn(Map.of(trackedItem.getMarketHashName(), stored));
        when(priceFeedService.getCachedItemPriceData(trackedItem.getMarketHashName())).thenReturn(current);

        var result = service.toggleBookmark(42L, user).item();

        assertEquals(new BigDecimal("45.00"), result.getPrice());
        assertEquals(current.freshness, result.getPriceFreshness());
        verify(priceFeedService, never()).refreshPriceCacheIfNeeded();
    }

    private static PriceFeedService.ItemPriceData cachedPrice(String price, Instant date) {
        return new PriceFeedService.ItemPriceData(new BigDecimal(price),
                new PriceFreshnessDto("FRESH", "CSGOTRADER_24H", date, date, date, date, false, false, false));
    }

    private void stubTrackedPage() {
        when(itemRepository.findTrackedPage(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(false), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(trackedItem)));
    }

    @Test
    void addTrackedItemUsesTrustedCatalogueValues() {
        CsItem catalogueItem = CsItem.builder()
                .id("skin-42")
                .name("AK-47 | Redline (Field-Tested)")
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .image("image-url")
                .build();
        TrackedItemCreateRequest request = new TrackedItemCreateRequest(
                "untrusted name", "untrusted hash", "skin-42", 0, null, null
        );
        when(csItemRepository.findById("skin-42")).thenReturn(Optional.of(catalogueItem));
        when(creationService.create(any(Item.class), org.mockito.ArgumentMatchers.isNull())).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            saved.setId(42L);
            return TrackedItemMapper.toDto(saved, null);
        });

        var result = service.addTrackedItem(request, user);

        assertEquals(catalogueItem.getName(), result.getItemName());
        assertEquals(catalogueItem.getMarketHashName(), result.getMarketHashName());
        assertEquals(1, result.getQuantity());
        verify(priceFeedService).refreshPriceCacheIfNeeded();
        verify(priceFeedService, never()).getItemPriceData(any());
    }

    @Test
    void catalogueRecordWithoutMarketIdentifierIsRejectedBeforeRefreshOrInsert() {
        when(csItemRepository.findById("missing-hash")).thenReturn(Optional.of(
                CsItem.builder().id("missing-hash").name("Unusable legacy entry").build()));
        assertThrows(IllegalArgumentException.class, () -> service.addTrackedItem(
                new TrackedItemCreateRequest(null, null, "missing-hash", 1, false, null), user));
        org.mockito.Mockito.verifyNoInteractions(priceFeedService, creationService);
        verify(itemRepository, never()).save(any());
    }

    @Test
    void duplicateTrackedItemIsRejectedBeforeSaving() {
        CsItem catalogueItem = CsItem.builder()
                .id("skin-42")
                .name(trackedItem.getItemName())
                .marketHashName(trackedItem.getMarketHashName())
                .build();
        when(csItemRepository.findById("skin-42")).thenReturn(Optional.of(catalogueItem));
        when(itemRepository.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                7, trackedItem.getMarketHashName())).thenReturn(true);

        assertThrows(DuplicateTrackedItemException.class, () -> service.addTrackedItem(
                new TrackedItemCreateRequest(null, null, "skin-42", 1, false, null), user
        ));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void catalogueSearchUsesTheSameTrustedMappingAsAnExplicitCatalogueId() {
        CsItem catalogueItem = CsItem.builder().id("skin-42").name("Trusted item name")
                .marketHashName("Trusted market hash").image("image-url").build();
        when(csItemRepository.findBestMatch(org.mockito.ArgumentMatchers.eq("requested hash"), any()))
                .thenReturn(List.of(catalogueItem));
        when(creationService.create(any(Item.class), org.mockito.ArgumentMatchers.isNull())).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            saved.setId(42L);
            return TrackedItemMapper.toDto(saved, null);
        });

        var result = service.addTrackedItem(new TrackedItemCreateRequest(
                "untrusted name", "requested hash", null, 2, false, null), user);

        assertEquals("skin-42", result.getCsItemId());
        assertEquals("Trusted item name", result.getItemName());
        assertEquals("Trusted market hash", result.getMarketHashName());
        assertEquals("image-url", result.getImage());
        verify(csItemRepository, never()).save(any());
        verify(priceFeedService).refreshPriceCacheIfNeeded();
    }

    @Test
    void priceUpdateRejectsItemsOwnedByAnotherUser() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(99L), 7))
                .thenReturn(List.of());

        assertThrows(ItemNotFoundException.class, () -> service.requestPriceUpdate(List.of(99L), user));

        verify(applicationTaskExecutor, never()).execute(any(Runnable.class));
        verify(priceFeedService, never()).updatePricesForItems(anyList());
    }

    @Test
    void priceUpdateRunsOnlyForValidatedOwnedIds() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(42L), 7))
                .thenReturn(List.of(trackedItem));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationTaskExecutor).execute(any(Runnable.class));

        var result = service.requestPriceUpdate(List.of(42L, 42L), user);

        assertEquals(1, result.itemCount());
        verify(priceFeedService).updatePricesForItems(List.of(42L));
    }

    @Test
    void deletingAnotherUsersItemIsRejected() {
        when(itemRepository.findLockedTrackedItem(42L, 7)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> service.deleteTrackedItem(42L, user));

        verify(itemRepository, never()).delete(any(Item.class));
    }
}
