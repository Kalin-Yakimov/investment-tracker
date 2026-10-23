package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.pricing.PriceFeedService;
import com.kalin.investment_tracker.pricing.MarketPricePersistenceService;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.shared.web.ApiMessageResponse;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshotDto;
import com.kalin.investment_tracker.exception.ItemNotFoundException;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshot;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.pricing.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.pricing.PriceHistoryRepository;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.validation.ApiRequestLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrackedItemService {

    private static final Duration PRICE_HISTORY_WINDOW = Duration.ofDays(120);

    private final PriceFeedService priceFeedService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final TaskExecutor applicationTaskExecutor;
    private final ActivityEventService activityEventService;
    private final TrackedItemCreationService creationService;
    private final MarketPricePersistenceService marketPrices;
    private final Set<Integer> pendingPriceUpdates = ConcurrentHashMap.newKeySet();

    public Page<TrackedItemDto> getTrackedItems(User user, boolean dashboardOnly, int page, int size) {
        ApiRequestLimits.requirePage(page, size, ApiRequestLimits.PAGE_SIZE);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Item> items = itemRepository.findTrackedPage(user.getId(), dashboardOnly, pageable);

        if (items.stream().anyMatch(item -> hasText(item.getMarketHashName()))) {
            priceFeedService.refreshPriceCacheIfNeeded();
            // The refresh may have updated item.price; do not describe an earlier detached row.
            items = itemRepository.findTrackedPage(user.getId(), dashboardOnly, pageable);
        }
        Map<String, MarketPricePersistenceService.RecordedMarketPrice> stored = marketPrices
                .findLatestPrices(items.stream().map(Item::getMarketHashName).toList());
        return items.map(item -> mapTrackedItem(item, stored.get(item.getMarketHashName())));
    }

    @Transactional(propagation = Propagation.NEVER)
    public TrackedItemDto addTrackedItem(TrackedItemCreateRequest request, User user) {
        ResolvedCatalogueItem resolved = resolveCatalogueItem(request);
        String marketHashName = resolved.marketHashName();
        if (!hasText(marketHashName)) {
            throw new IllegalArgumentException("The catalogue item has no market identifier");
        }

        if (itemRepository.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                user.getId(), marketHashName)) {
            throw new DuplicateTrackedItemException();
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

        priceFeedService.refreshPriceCacheIfNeeded();
        return creationService.create(item, priceFeedService.getCachedPublication());
    }

    public PriceUpdateResponse requestPriceUpdate(List<Long> requestedIds, User user) {
        if (requestedIds == null || requestedIds.isEmpty()
                || requestedIds.size() > ApiRequestLimits.PRICE_UPDATE_BATCH_SIZE
                || requestedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Provide between 1 and " + ApiRequestLimits.PRICE_UPDATE_BATCH_SIZE + " positive item IDs");
        }
        List<Long> itemIds = requestedIds.stream().distinct().toList();
        List<Item> ownedItems = itemRepository
                .findAllByIdInAndUserIdAndSteamFoundCheckTrue(itemIds, user.getId());
        if (ownedItems.size() != itemIds.size()) {
            throw new ItemNotFoundException(itemIds.getFirst());
        }

        List<Long> ownedItemIds = ownedItems.stream().map(Item::getId).toList();
        Integer userId = user.getId();
        if (!pendingPriceUpdates.add(userId)) {
            throw new IllegalArgumentException("A price update for this account is already pending");
        }
        try {
            activityEventService.recordUserAction(user, "PRICE_UPDATE_REQUESTED",
                    "Requested a price update for " + ownedItemIds.size() + " tracked item(s)", null);
            applicationTaskExecutor.execute(() -> {
                try { priceFeedService.updatePricesForItems(ownedItemIds); }
                catch (Exception failure) { log.warn("Asynchronous price update failed ({})", failure.getClass().getSimpleName()); }
                finally { pendingPriceUpdates.remove(userId); }
            });
        } catch (RejectedExecutionException busy) {
            pendingPriceUpdates.remove(userId);
            throw new ExternalServiceException("The background task queue is full; try again later");
        } catch (RuntimeException failure) {
            pendingPriceUpdates.remove(userId);
            throw failure;
        }

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
                        .sourcePublishedAt(snapshot.getSourcePublishedAt())
                        .fxRateAsOf(snapshot.getFxRateAsOf())
                        .build())
                .toList();
    }

    @Transactional
    public TrackedItemDto updateTrackedItem(Long id, TrackedItemUpdateRequest request, User user) {
        Item item = findOwnedItemForMutation(id, user);
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
        return mapTrackedItem(updatedItem);
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long id, User user) {
        Item item = findOwnedItemForMutation(id, user);
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
                mapTrackedItem(updatedItem)
        );
    }

    @Transactional
    public ApiMessageResponse deleteTrackedItem(Long id, User user) {
        Item item = findOwnedItemForMutation(id, user);
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
                priceFeedService.validateItemOnSteam(marketHashName),
                marketHashName
        );
    }

    public TrackedItemDto getTrackedItemDetails(Long id, User user) {
        // Verify ownership before performing potentially costly public-source work.
        findOwnedItem(id, user);
        priceFeedService.refreshPriceCacheIfNeeded();
        Item item = findOwnedItem(id, user);
        return mapTrackedItem(item);
    }

    private TrackedItemDto mapTrackedItem(Item item) {
        var stored = marketPrices.findLatestPrices(java.util.Collections.singletonList(item.getMarketHashName()));
        return mapTrackedItem(item, stored.get(item.getMarketHashName()));
    }

    private TrackedItemDto mapTrackedItem(Item item, MarketPricePersistenceService.RecordedMarketPrice stored) {
        var cached = priceFeedService.getCachedItemPriceData(item.getMarketHashName());
        boolean cacheAtLeastAsNew = cached != null && (cached.freshness == null || stored == null
                || cached.freshness.observedAt() != null && !cached.freshness.observedAt().isBefore(stored.observedAt()));
        // A mutation can join an old repeatable-read snapshot. Never attach old metadata
        // to the current locking-read price if neither candidate matches that price.
        boolean inconsistentStoredRead = stored != null && item.getPrice() != null
                && stored.price().compareTo(item.getPrice()) != 0;
        if (cacheAtLeastAsNew && (!inconsistentStoredRead || cached.freshness == null
                || cached.freshness.observedAt() != null && cached.freshness.observedAt().isAfter(stored.observedAt()))) {
            return TrackedItemMapper.toDto(item, cached);
        }
        return TrackedItemMapper.toDto(item, stored != null && !inconsistentStoredRead
                ? priceFeedService.describeStoredPrice(stored) : null);
    }

    private ResolvedCatalogueItem resolveCatalogueItem(TrackedItemCreateRequest request) {
        if (hasText(request.csItemId())) {
            CsItem catalogueItem = csItemRepository.findById(request.csItemId())
                    .orElseThrow(() -> new IllegalArgumentException("CS item was not found in the catalogue"));
            return ResolvedCatalogueItem.fromCatalogue(catalogueItem);
        }

        if (hasText(request.marketHashName())) {
            List<CsItem> matches = csItemRepository.findBestMatch(
                    request.marketHashName(),
                    PageRequest.of(0, 1)
            );
            if (!matches.isEmpty()) {
                return ResolvedCatalogueItem.fromCatalogue(matches.getFirst());
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
        return itemRepository.findTrackedItemByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    private Item findOwnedItemForMutation(Long itemId, User user) {
        return itemRepository.findLockedTrackedItem(itemId, user.getId())
                .orElseThrow(() -> new ItemNotFoundException(itemId));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ResolvedCatalogueItem(CsItem catalogueItem, String itemName, String marketHashName) {
        private static ResolvedCatalogueItem fromCatalogue(CsItem item) {
            return new ResolvedCatalogueItem(item, item.getName(), item.getMarketHashName());
        }
    }
}
