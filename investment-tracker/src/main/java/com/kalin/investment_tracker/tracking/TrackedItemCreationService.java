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

    @Transactional
    public TrackedItemDto create(Item item, PriceFeedSnapshot feed) {
        // Match scheduler lock order: publication guard -> item rows -> observation writes.
        // DB uniqueness remains the final protection, including imports/other instances.
        prices.lockForTrackingCreation();
        if (items.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                item.getUser().getId(), item.getMarketHashName())) {
            throw new DuplicateTrackedItemException();
        }
        prices.initializeTrackedPrice(item, feed);
        Item saved = items.saveAndFlush(item);
        activity.recordUserAction(item.getUser(), "TRACKED_ITEM_ADDED",
                "Started tracking '" + saved.getItemName() + "'", null);
        return TrackedItemMapper.toDto(saved, saved.getPrice() == null ? null
                : new PriceFeedService.ItemPriceData(saved.getPrice()));
    }
}
