package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.dto.TrackedItemCreateRequest;
import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedItemServiceTest {

    @Mock private SteamPriceService steamPriceService;
    @Mock private PriceHistoryRepository priceHistoryRepository;
    @Mock private MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private CsItemRepository csItemRepository;
    @Mock private TaskExecutor applicationTaskExecutor;
    @Mock private ActivityEventService activityEventService;

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
        trackedItem.setCsItemId("skin-42");
        trackedItem.setQuantity(1);
        trackedItem.setPrice(new BigDecimal("45.00"));
        trackedItem.setSteamFoundCheck(true);
        trackedItem.setUser(user);
    }

    @Test
    void trackedItemsAreAlwaysLoadedForAuthenticatedUser() {
        when(itemRepository.findSteamTrackedItemsByUserId(7)).thenReturn(List.of(trackedItem));
        when(csItemRepository.findAllById(any())).thenReturn(List.of());

        var result = service.getTrackedItems(user, false);

        assertEquals(1, result.size());
        verify(itemRepository).findSteamTrackedItemsByUserId(7);
        verify(itemRepository, never()).findAllSteamTrackedItems();
    }

    @Test
    void currentFeedPriceOverridesStoredDisplayPrice() {
        when(itemRepository.findSteamTrackedItemsByUserId(7)).thenReturn(List.of(trackedItem));
        when(csItemRepository.findAllById(any())).thenReturn(List.of());
        when(steamPriceService.getItemPriceData(trackedItem.getMarketHashName()))
                .thenReturn(new SteamPriceService.ItemPriceData(new BigDecimal("52.75")));

        var result = service.getTrackedItems(user, false);

        assertEquals(new BigDecimal("52.75"), result.getFirst().getPrice());
        assertEquals(new BigDecimal("52.75"), result.getFirst().getPrice24h());
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
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        var result = service.addTrackedItem(request, user);

        assertEquals(catalogueItem.getName(), result.getItemName());
        assertEquals(catalogueItem.getMarketHashName(), result.getMarketHashName());
        assertEquals(1, result.getQuantity());
        verify(steamPriceService).recordCurrentSnapshotForItem(any(Item.class));
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

        assertThrows(IllegalArgumentException.class, () -> service.addTrackedItem(
                new TrackedItemCreateRequest(null, null, "skin-42", 1, false, null), user
        ));

        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void priceUpdateRejectsItemsOwnedByAnotherUser() {
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(99L), 7))
                .thenReturn(List.of());

        assertThrows(ItemNotFoundException.class, () -> service.requestPriceUpdate(List.of(99L), user));

        verify(applicationTaskExecutor, never()).execute(any(Runnable.class));
        verify(steamPriceService, never()).updatePricesForItems(anyList());
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
        verify(steamPriceService).updatePricesForItems(List.of(42L));
    }

    @Test
    void deletingAnotherUsersItemIsRejected() {
        when(itemRepository.findByIdAndUserId(42L, 7)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> service.deleteTrackedItem(42L, user));

        verify(itemRepository, never()).delete(any(Item.class));
    }
}
