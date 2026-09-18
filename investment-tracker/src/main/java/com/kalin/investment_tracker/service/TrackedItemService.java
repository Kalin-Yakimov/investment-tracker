package com.kalin.investment_tracker.service;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.dto.ApiMessageResponse;
import com.kalin.investment_tracker.dto.BookmarkResponse;
import com.kalin.investment_tracker.dto.MarketItemValidationResponse;
import com.kalin.investment_tracker.dto.MarketPriceSnapshotDto;
import com.kalin.investment_tracker.dto.PriceUpdateResponse;
import com.kalin.investment_tracker.dto.SteamItemDto;
import com.kalin.investment_tracker.dto.TrackedItemCreateRequest;
import com.kalin.investment_tracker.dto.TrackedItemUpdateRequest;
import com.kalin.investment_tracker.exeption.ItemNotFoundException;
import com.kalin.investment_tracker.model.CsItem;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackedItemService {

    private static final Duration PRICE_HISTORY_WINDOW = Duration.ofDays(120);

    private final SteamPriceService steamPriceService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final TaskExecutor applicationTaskExecutor;
    private final ActivityEventService activityEventService;

    public List<SteamItemDto> getTrackedItems(User user, boolean dashboardOnly) {
        List<Item> items = itemRepository.findSteamTrackedItemsByUserId(user.getId());
        if (dashboardOnly) {
            items = items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getShowInDashboard()))
                    .toList();
        }

        return items.stream()
                .map(this::toDto)
                .toList();
    }

    public SteamItemDto addTrackedItem(TrackedItemCreateRequest request, User user) {
        ResolvedCatalogueItem resolved = resolveCatalogueItem(request);
        String marketHashName = resolved.marketHashName();

        if (itemRepository.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                user.getId(), marketHashName)) {
            throw new IllegalArgumentException("You are already tracking this item");
        }

        Item item = new Item();
        item.setItemName(resolved.itemName());
        item.setMarketHashName(marketHashName);
        item.setCatalogueItem(resolved.catalogueItem());
        item.setQuantity(request.quantity() > 0 ? request.quantity() : 1);
        item.setShowInDashboard(Boolean.TRUE.equals(request.showInDashboard()));
        item.setPriceNotification(request.priceNotification());
        item.setSteamFoundCheck(true);
        item.setUser(user);

        steamPriceService.tryFetchPrice(marketHashName).ifPresent(item::setPrice);
        Item savedItem = itemRepository.save(item);

        activityEventService.recordUserAction(
                user,
                "TRACKED_ITEM_ADDED",
                "Started tracking '" + savedItem.getItemName() + "'",
                null
        );
        steamPriceService.recordCurrentSnapshotForItem(savedItem);

        log.info("Item '{}' saved for user {} (price={})",
                savedItem.getMarketHashName(), user.getId(),
                savedItem.getPrice() != null ? savedItem.getPrice() : "pending scheduler");
        return toDto(savedItem);
    }

    public PriceUpdateResponse requestPriceUpdate(List<Long> requestedIds, User user) {
        List<Long> itemIds = requestedIds.stream().distinct().toList();
        List<Item> ownedItems = itemRepository
                .findAllByIdInAndUserIdAndSteamFoundCheckTrue(itemIds, user.getId());
        if (ownedItems.size() != itemIds.size()) {
            throw new ItemNotFoundException(itemIds.getFirst());
        }

        List<Long> ownedItemIds = ownedItems.stream().map(Item::getId).toList();
        applicationTaskExecutor.execute(() -> {
            try {
                steamPriceService.updatePricesForItems(ownedItemIds);
            } catch (Exception exception) {
                log.error("Error in asynchronous price update", exception);
            }
        });

        activityEventService.recordUserAction(
                user,
                "PRICE_UPDATE_REQUESTED",
                "Requested a price update for " + ownedItemIds.size() + " tracked item(s)",
                null
        );

        return new PriceUpdateResponse(
                "Price update started for " + ownedItemIds.size() + " items",
                ownedItemIds.size()
        );
    }

    @Transactional(readOnly = true)
    public List<MarketPriceSnapshotDto> getPriceHistory(Long itemId, User user) {
        Item item = findOwnedItem(itemId, user);
        Instant cutoff = Instant.now().minus(PRICE_HISTORY_WINDOW);

        return marketPriceSnapshotRepository
                .findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                        item.getMarketHashName(),
                        MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                        cutoff
                )
                .stream()
                .map(snapshot -> MarketPriceSnapshotDto.builder()
                        .id(snapshot.getId())
                        .price(snapshot.getPriceEur())
                        .dateExtracted(snapshot.getObservedAt())
                        .source(snapshot.getSource())
                        .build())
                .toList();
    }

    @Transactional
    public SteamItemDto updateTrackedItem(Long id, TrackedItemUpdateRequest request, User user) {
        Item item = findOwnedItem(id, user);
        if (request.showInDashboard() != null) {
            item.setShowInDashboard(request.showInDashboard());
        }
        if (request.priceNotification() != null) {
            item.setPriceNotification(request.priceNotification());
        }

        Item updatedItem = itemRepository.save(item);
        activityEventService.recordUserAction(
                user,
                "TRACKED_ITEM_UPDATED",
                "Updated tracking settings for '" + updatedItem.getItemName() + "'",
                null
        );
        return toDto(updatedItem);
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long id, User user) {
        Item item = findOwnedItem(id, user);
        item.setShowInDashboard(!Boolean.TRUE.equals(item.getShowInDashboard()));
        Item updatedItem = itemRepository.save(item);

        activityEventService.recordUserAction(
                user,
                "TRACKED_ITEM_BOOKMARK_CHANGED",
                "Changed dashboard visibility for '" + updatedItem.getItemName() + "'",
                null
        );

        return new BookmarkResponse(
                Boolean.TRUE.equals(updatedItem.getShowInDashboard()),
                toDto(updatedItem)
        );
    }

    @Transactional
    public ApiMessageResponse deleteTrackedItem(Long id, User user) {
        Item item = findOwnedItem(id, user);
        priceHistoryRepository.deleteByItemId(item.getId());
        itemRepository.delete(item);

        activityEventService.recordUserAction(
                user,
                "TRACKED_ITEM_DELETED",
                "Stopped tracking '" + item.getItemName() + "'",
                null
        );
        return new ApiMessageResponse("Item deleted successfully");
    }

    public MarketItemValidationResponse validateMarketItem(String marketHashName) {
        return new MarketItemValidationResponse(
                steamPriceService.validateItemOnSteam(marketHashName),
                marketHashName
        );
    }

    public SteamItemDto getTrackedItemDetails(Long id, User user) {
        Item item = findOwnedItem(id, user);
        return toDto(item);
    }

    private ResolvedCatalogueItem resolveCatalogueItem(TrackedItemCreateRequest request) {
        if (hasText(request.csItemId())) {
            CsItem catalogueItem = csItemRepository.findById(request.csItemId())
                    .orElseThrow(() -> new IllegalArgumentException("CS item was not found in the catalogue"));
            return new ResolvedCatalogueItem(
                    catalogueItem,
                    catalogueItem.getName(),
                    catalogueItem.getMarketHashName()
            );
        }

        if (hasText(request.marketHashName())) {
            List<CsItem> matches = csItemRepository.findBestMatch(
                    request.marketHashName(),
                    PageRequest.of(0, 1)
            );
            if (!matches.isEmpty()) {
                CsItem catalogueItem = matches.getFirst();
                return new ResolvedCatalogueItem(
                        catalogueItem,
                        catalogueItem.getName(),
                        catalogueItem.getMarketHashName()
                );
            }
            return new ResolvedCatalogueItem(
                    null,
                    hasText(request.itemName()) ? request.itemName().trim() : request.marketHashName().trim(),
                    request.marketHashName().trim()
            );
        }

        throw new IllegalArgumentException("marketHashName is required");
    }

    private Item findOwnedItem(Long itemId, User user) {
        return itemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    private SteamItemDto toDto(Item item) {
        CsItem catalogueItem = item.getCatalogueItem();
        BigDecimal currentPrice = item.getPrice();
        BigDecimal price24h = null;
        if (hasText(item.getMarketHashName())) {
            SteamPriceService.ItemPriceData priceData = steamPriceService.getItemPriceData(item.getMarketHashName());
            if (priceData != null) {
                currentPrice = priceData.currentPrice;
                price24h = priceData.price24h;
            }
        }

        return SteamItemDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .marketHashName(item.getMarketHashName())
                .csItemId(catalogueItem != null ? catalogueItem.getId() : null)
                .quantity(item.getQuantity())
                .price(currentPrice)
                .boughtPrice(item.getBoughtPrice())
                .boughtDate(item.getBoughtDate())
                .showInDashboard(item.getShowInDashboard())
                .priceNotification(item.getPriceNotification())
                .steamFoundCheck(item.getSteamFoundCheck())
                .image(catalogueItem != null ? catalogueItem.getImage() : null)
                .price24h(price24h)
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResolvedCatalogueItem(CsItem catalogueItem, String itemName, String marketHashName) {
    }
}
