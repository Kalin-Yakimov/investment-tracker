package com.kalin.investment_tracker.inventory;

import com.kalin.investment_tracker.exception.ExternalServiceException;
import com.kalin.investment_tracker.exception.PrivateSteamInventoryException;
import com.kalin.investment_tracker.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InventoryApiTest {
    private SteamInventoryService service;
    private MockMvc mvc;
    private static final String PATH = "/api/v1/inventory/calculate";
    private static final String BODY = "{\"steamUrl\":\"76561198000000001\"}";

    @BeforeEach void setup() {
        service = mock(SteamInventoryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SteamInventoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void privateInventoryKeepsItsPrivacyCode() throws Exception {
        when(service.calculateInventory(anyString())).thenThrow(new PrivateSteamInventoryException("Inventory is private"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRIVACY_PRIVATE"));
    }
    @Test void incompleteInventoryIsUnavailableRatherThanASuccessfulEmptyResult() throws Exception {
        when(service.calculateInventory(anyString())).thenThrow(new ExternalServiceException("Incomplete inventory"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("Incomplete inventory"));
    }
    @Test void partialValuationSerializesUnknownPricesAsNull() throws Exception {
        when(service.calculateInventory(anyString())).thenReturn(SteamInventoryResponseDto.builder()
                .totalValue(BigDecimal.ZERO).totalItemCount(2).uniqueItemsCount(1).unpricedItemCount(2)
                .items(List.of(SteamInventoryItemDto.builder().itemName("Unknown").quantity(2).build())).build());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unpricedItemCount").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(nullValue()))
                .andExpect(jsonPath("$.items[0].totalPrice").value(nullValue()));
    }
    @Test void emptyInputIsRejectedBeforeTheService() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"steamUrl\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
