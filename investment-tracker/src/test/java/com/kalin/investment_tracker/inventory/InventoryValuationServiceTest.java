package com.kalin.investment_tracker.inventory;

import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.pricing.PriceFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kalin.investment_tracker.inventory.SteamCommunityClient.*;

@ExtendWith(MockitoExtension.class)
class InventoryValuationServiceTest {
    private static final String ID = SteamCommunityClientTest.ID;
    @Mock SteamCommunityClient steam;
    @Mock CsItemRepository catalogue;
    @Mock PriceFeedService prices;
    private InventoryValuationService valuation;
    private final List<Asset> assets = new ArrayList<>();
    private final Map<DescriptionKey, Description> descriptions = new HashMap<>();

    @BeforeEach void setup() {
        valuation = new InventoryValuationService(steam, catalogue, prices, new SteamWorkLimiter(new SteamInventoryProperties()));
        when(steam.loadInventory(ID)).thenAnswer(call -> new Inventory(assets, descriptions));
        lenient().when(steam.fetchProfile(ID)).thenReturn(new Profile("Example", null));
    }
    private void add(String classId, String marketName, String name, int amount, boolean tradable) {
        DescriptionKey key = new DescriptionKey(classId, "0");
        assets.add(new Asset(Integer.toString(assets.size() + 1), key, amount));
        descriptions.put(key, new Description(marketName, name, "image_123", tradable));
    }
    private CsItem entry(String id, String marketName, String name, String image) {
        CsItem item = new CsItem();
        item.setId(id); item.setMarketHashName(marketName); item.setName(name); item.setImage(image);
        return item;
    }
    @Test void aggregatesStackedQuantitiesAndMixedTradability() {
        add("10", "Revolution Case", "Revolution Case", 3, true);
        add("11", "Revolution Case", "Revolution Case", 2, false);
        when(prices.getCurrentPrices(List.of("Revolution Case"))).thenReturn(Map.of("Revolution Case", new BigDecimal("2.50")));
        var response = valuation.calculate(ID);
        var item = response.getItems().getFirst();
        assertEquals(5, response.getTotalItemCount());
        assertEquals(1, response.getUniqueItemsCount());
        assertEquals(new BigDecimal("12.50"), response.getTotalValue());
        assertEquals(3, item.getTradableQuantity());
        assertEquals(2, item.getNonTradableQuantity());
        assertFalse(item.isTradable());
        assertEquals(5, response.getCategoryCounts().get("Cases"));
        verify(prices).getCurrentPrices(List.of("Revolution Case"));
    }
    @Test void keepsUnpricedAndUnmarketableItemsWithoutInventingZeroPrices() {
        add("10", "Revolution Case", "Revolution Case", 3, true);
        add("11", "Unknown Item", "Unknown Item", 2, true);
        add("12", null, "Service Medal", 1, false);
        when(prices.getCurrentPrices(any())).thenReturn(Map.of("Revolution Case", new BigDecimal("2.50")));
        var response = valuation.calculate(ID);
        assertEquals(6, response.getTotalItemCount());
        assertEquals(3, response.getUnpricedItemCount());
        assertEquals(new BigDecimal("7.50"), response.getTotalValue());
        assertEquals(3, response.getUniqueItemsCount());
        var medal = response.getItems().stream().filter(item -> item.getItemName().equals("Service Medal")).findFirst().orElseThrow();
        assertNull(medal.getMarketHashName());
        assertNull(medal.getUnitPrice());
        assertNull(medal.getTotalPrice());
        assertEquals(3, response.getCategoryCounts().get("Other"));
        verify(prices).getCurrentPrices(List.of("Revolution Case", "Unknown Item"));
    }
    @Test void allUnmarketableInventoryDoesNotRequestPricesOrCatalogue() {
        add("12", null, "Service Medal", 1, false);
        var response = valuation.calculate(ID);
        assertEquals(1, response.getUnpricedItemCount());
        assertNull(response.getMostValuableItem());
        verifyNoInteractions(prices, catalogue);
    }
    @Test void emptyInventoryDoesNotRequestPricesOrCatalogue() {
        var response = valuation.calculate(ID);
        assertEquals(new BigDecimal("0.00"), response.getTotalValue());
        assertEquals(0, response.getTotalItemCount());
        assertTrue(response.getItems().isEmpty());
        verifyNoInteractions(prices, catalogue);
    }
    @Test void priceSourceOutageDoesNotReturnAnAllZeroInventory() {
        add("10", "Revolution Case", "Revolution Case", 1, true);
        when(prices.getCurrentPrices(any())).thenThrow(new ExternalServiceException("Prices unavailable"));
        assertThrows(ExternalServiceException.class, () -> valuation.calculate(ID));
        verify(steam, never()).fetchProfile(any());
    }
    @Test void mostValuableMeansUnitPriceWhileTheTableIsSortedByTotalValue() {
        add("10", "Revolution Case", "Revolution Case", 10, true);
        add("11", "Expensive Item", "Expensive Item", 1, true);
        when(prices.getCurrentPrices(any())).thenReturn(Map.of("Revolution Case", new BigDecimal("2.50"), "Expensive Item", new BigDecimal("10.00")));
        var response = valuation.calculate(ID);
        assertEquals("Revolution Case", response.getItems().getFirst().getItemName());
        assertEquals("Expensive Item", response.getMostValuableItem().getItemName());
    }
    @Test void equalValuesHaveDeterministicNameOrderingAndZeroIsNotMissing() {
        add("10", "B", "B", 1, true);
        add("11", "A", "A", 1, true);
        when(prices.getCurrentPrices(any())).thenReturn(Map.of("A", BigDecimal.ZERO, "B", BigDecimal.ZERO));
        var response = valuation.calculate(ID);
        assertEquals(List.of("A", "B"), response.getItems().stream().map(item -> item.getItemName()).toList());
        assertEquals("A", response.getMostValuableItem().getItemName());
        assertEquals(0, response.getUnpricedItemCount());
    }
    @Test void catalogueExactIdentityWinsOverAnAmbiguousDisplayName() {
        add("10", "Revolution Case", "Revolution Case", 1, true);
        when(catalogue.findCatalogueEntriesForMarketNames(any())).thenReturn(List.of(
                entry("a", "Different Item", "Revolution Case", "wrong"),
                entry("b", null, "Revolution Case", "legacy"),
                entry("c", "Revolution Case", "Revolution Case", "correct")));
        var item = valuation.calculate(ID).getItems().getFirst();
        assertEquals("c", item.getCsItemId());
        assertEquals("correct", item.getImage());
    }
    @Test void steamIconIsAFallbackForMissingCatalogueImages() {
        add("10", "Revolution Case", "Revolution Case", 1, true);
        assertEquals("https://community.akamai.steamstatic.com/economy/image/image_123",
                valuation.calculate(ID).getItems().getFirst().getImage());
    }
    @Test void catalogueQueriesAreBatchedInsteadOfOneUnboundedInClause() {
        for (int i = 0; i < 1001; i++) add(Integer.toString(i), "Item " + i, "Item " + i, 1, true);
        when(catalogue.findCatalogueEntriesForMarketNames(any())).thenAnswer(call -> {
            assertTrue(((Collection<?>) call.getArgument(0)).size() <= 500);
            return List.of();
        });
        assertEquals(1001, valuation.calculate(ID).getTotalItemCount());
        verify(catalogue, times(3)).findCatalogueEntriesForMarketNames(any());
        verify(prices, times(1)).getCurrentPrices(any());
    }
}
