package com.kalin.investment_tracker.portfolio;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.catalogue.CsItem;
import com.kalin.investment_tracker.catalogue.CsItemRepository;
import com.kalin.investment_tracker.item.Item;
import com.kalin.investment_tracker.item.ItemRepository;
import com.kalin.investment_tracker.item.ItemStatus;
import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioRequestMappingTest {
    @Mock ItemRepository items;
    @Mock CsItemRepository catalogue;
    @Mock ActivityEventService events;
    @InjectMocks PortfolioService service;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(7);
        when(items.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void addAndEditUseTheSameMappingWithoutChangingCatalogueOrOwnership(boolean edit, boolean useCatalogue) {
        CsItem entry = CsItem.builder().id("skin-1").name("Catalogue Case").marketHashName("Market Case").image("image-url").build();
        if (useCatalogue) when(catalogue.findById("skin-1")).thenReturn(Optional.of(entry));
        Item existing = new Item();
        existing.setId(42L);
        existing.setUser(owner);
        existing.setCatalogueItem(entry);
        existing.setMarketHashName("Previous market name");
        if (edit) when(items.findPortfolioItemByIdAndUserId(42L, 7)).thenReturn(Optional.of(existing));
        PortfolioItemRequest request = new PortfolioItemRequest(
                "manual case", useCatalogue ? "skin-1" : null, ItemStatus.SOLD,
                LocalDate.of(2026, 10, 11), 3, new BigDecimal("4.25"));

        PortfolioItemResponseDto result = edit ? service.updateItem(42L, request, owner) : service.addItem(request, owner);
        var saved = org.mockito.ArgumentCaptor.forClass(Item.class);
        verify(items).save(saved.capture());
        Item item = saved.getValue();
        assertSame(owner, item.getUser());
        assertEquals(request.itemStatus(), item.getItemStatus());
        assertEquals(request.date(), item.getDate());
        assertEquals(request.quantity(), item.getQuantity());
        assertEquals(request.price(), item.getPrice());
        assertEquals(item.getItemName(), result.getItemName());
        if (useCatalogue) {
            assertSame(entry, item.getCatalogueItem());
            assertEquals("Catalogue Case", item.getItemName());
            assertEquals("Market Case", item.getMarketHashName());
            assertEquals("skin-1", result.getCsItemId());
            assertEquals("image-url", result.getImage());
            verify(catalogue, never()).save(any());
        } else {
            assertEquals("manual case", item.getItemName());
            assertNull(item.getCatalogueItem());
            assertNull(item.getMarketHashName());
            assertNull(result.getCsItemId());
            assertNull(result.getImage());
            verifyNoInteractions(catalogue);
        }
    }
}
