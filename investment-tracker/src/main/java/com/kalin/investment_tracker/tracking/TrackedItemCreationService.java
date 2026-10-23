package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.pricing.MarketPricePersistenceService;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import com.kalin.investment_tracker.pricing.PriceFeedSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Database-only boundary: insert, initial observation and audit succeed or roll back together. */
@Service
@RequiredArgsConstructor
public class TrackedItemCreationService {
    private final ItemRepository items;
    private final MarketPricePersistenceService prices;
    private final ActivityEventService activity;
    private final PriceFeedService feedService;
    private final com.kalin.investment_tracker.validation.ResourceLimitsProperties limits;

    @Transactional
    public TrackedItemDto create(Item item, PriceFeedSnapshot feed) {
        // Match scheduler lock order: publication guard -> item rows -> observation writes.
        // DB uniqueness remains the final protection, including imports/other instances.
        prices.lockForTrackingCreation();
        if (items.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                item.getUser().getId(), item.getMarketHashName())) {
            throw new DuplicateTrackedItemException();
        }
        // The publication guard serializes all tracked insertions, including across instances.
        if (items.countTrackedItemsByUserId(item.getUser().getId()) >= limits.getTrackedItemsPerUser()) {
            throw new com.kalin.investment_tracker.validation.ResourceLimitException(org.springframework.http.HttpStatus.CONFLICT,
                    "Tracking limit reached (" + limits.getTrackedItemsPerUser() + " items). Remove an item before adding another.", 0);
        }
        prices.initializeTrackedPrice(item, feed);
        Item saved = items.saveAndFlush(item);
        activity.recordUserAction(item.getUser(), "TRACKED_ITEM_ADDED",
                "Started tracking '" + saved.getItemName() + "'", null);
        var stored = prices.findLatestPrices(java.util.List.of(saved.getMarketHashName())).get(saved.getMarketHashName());
        var cached = feedService.getCachedItemPriceData(saved.getMarketHashName());
        boolean matches = cached != null && saved.getPrice() != null && cached.currentPrice.compareTo(saved.getPrice()) == 0
                && (stored == null || cached.freshness != null && stored.observedAt().equals(cached.freshness.observedAt()));
        return TrackedItemMapper.toDto(saved, matches ? cached : stored == null ? null : feedService.describeStoredPrice(stored));
    }
}
