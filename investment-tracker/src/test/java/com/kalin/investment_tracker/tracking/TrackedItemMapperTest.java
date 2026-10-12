package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.pricing.PriceFeedService;

import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.item.Item;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class TrackedItemMapperTest {
    @Test void usesSuppliedPriceWithoutChangingStoredItemOrCatalogue() {
        Item item = new Item();
        item.setId(42L);
        item.setMarketHashName("Case");
        item.setPrice(new BigDecimal("4.00"));
        item.setShowInDashboard(true);
        item.setCatalogueItem(CsItem.builder().id("case-42").image("https://example.test/case.png").build());
        var dto = TrackedItemMapper.toDto(item, new PriceFeedService.ItemPriceData(new BigDecimal("5.00")));
        assertEquals(new BigDecimal("5.00"), dto.getPrice());
        assertEquals(new BigDecimal("5.00"), dto.getPrice24h());
        assertEquals("case-42", dto.getCsItemId());
        assertEquals(new BigDecimal("4.00"), item.getPrice());
        assertTrue(item.getShowInDashboard());
    }

    @Test void preservesStoredPriceAndNullCatalogueWhenTheFeedHasNoPrice() {
        Item item = new Item();
        item.setPrice(BigDecimal.ZERO);
        var dto = TrackedItemMapper.toDto(item, null);
        assertEquals(BigDecimal.ZERO, dto.getPrice());
        assertNull(dto.getPrice24h());
        assertNull(dto.getCsItemId());
        assertNull(dto.getImage());
    }
}
