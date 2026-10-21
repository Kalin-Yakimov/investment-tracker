package com.kalin.investment_tracker.tracking;

import com.kalin.investment_tracker.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedItemControllerTest {

    @Mock
    private TrackedItemService trackedItemService;

    @InjectMocks
    private TrackedItemController controller;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1);
    }

    @Test
    void getAllItemsDelegatesToTrackedItemService() {
        TrackedItemDto item = TrackedItemDto.builder().id(10L).itemName("AK-47 | Redline").build();
        when(trackedItemService.getTrackedItems(user, true, 0, 20)).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(item)));

        var response = controller.getAllItems(user, true, 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(item), response.getBody().content());
        verify(trackedItemService).getTrackedItems(user, true, 0, 20);
    }

    @Test
    void addItemUsesDedicatedRequestDto() {
        TrackedItemCreateRequest request = new TrackedItemCreateRequest(
                "AK-47 | Redline", null, "skin-1", 1, false, null
        );
        TrackedItemDto result = TrackedItemDto.builder().id(1L).build();
        when(trackedItemService.addTrackedItem(request, user)).thenReturn(result);

        var response = controller.addItem(request, user);

        assertEquals(result, response.getBody());
        verify(trackedItemService).addTrackedItem(request, user);
    }

    @Test
    void priceUpdateUsesTypedRequestAndResponse() {
        PriceUpdateRequest request = new PriceUpdateRequest(List.of(1L, 2L));
        PriceUpdateResponse result = new PriceUpdateResponse("started", 2);
        when(trackedItemService.requestPriceUpdate(request.itemIds(), user)).thenReturn(result);

        var response = controller.updatePrices(request, user);

        assertEquals(result, response.getBody());
        verify(trackedItemService).requestPriceUpdate(List.of(1L, 2L), user);
    }

    @Test
    void bookmarkResponseKeepsExistingApiShape() {
        TrackedItemDto item = TrackedItemDto.builder().id(1L).showInDashboard(true).build();
        BookmarkResponse result = new BookmarkResponse(true, item);
        when(trackedItemService.toggleBookmark(1L, user)).thenReturn(result);

        var response = controller.toggleBookmark(1L, user);

        assertEquals(result, response.getBody());
    }

    @Test
    void validationDelegatesToService() {
        MarketItemValidationRequest request = new MarketItemValidationRequest("AK-47 | Redline");
        MarketItemValidationResponse result = new MarketItemValidationResponse(true, request.marketHashName());
        when(trackedItemService.validateMarketItem(request.marketHashName())).thenReturn(result);

        var response = controller.validateItem(request);

        assertEquals(result, response.getBody());
    }
}
