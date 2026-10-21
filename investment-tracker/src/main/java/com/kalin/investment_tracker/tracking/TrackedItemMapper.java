package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.pricing.PriceFeedService;

import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.item.Item;
import java.math.BigDecimal;

/** Pure response mapping; the caller supplies price data and owns any refresh. */
public final class TrackedItemMapper {
    private TrackedItemMapper() {}

    public static TrackedItemDto toDto(Item item, PriceFeedService.ItemPriceData priceData) {
        CsItem catalogueItem = item.getCatalogueItem();
        BigDecimal currentPrice = item.getPrice();
        BigDecimal price24h = null;
        if (priceData != null) {
            currentPrice = priceData.currentPrice;
            price24h = priceData.price24h;
        }

        return TrackedItemDto.builder()
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
                .priceFreshness(priceData != null && priceData.freshness != null ? priceData.freshness
                        : com.kalin.investment_tracker.pricing.PriceFreshnessDto.unknownStored(currentPrice != null))
                .build();
    }

}
