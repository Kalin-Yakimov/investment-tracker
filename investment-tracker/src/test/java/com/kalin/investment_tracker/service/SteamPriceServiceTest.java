package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.PriceHistory;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SteamPriceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private SteamPriceService steamPriceService;

    private Item testItem;

    @BeforeEach
    void setUp() {
        testItem = new Item();
        testItem.setId(1L);
        testItem.setItemName("AK-47 | Redline (Field-Tested)");
        testItem.setMarketHashName("AK-47 | Redline (Field-Tested)");
        testItem.setQuantity(1);
        testItem.setPrice(new BigDecimal("50.00"));
        testItem.setShowInDashboard(true);
    }

    @Test
    void testFetchCurrentPrice_Success() throws Exception {
        // Arrange
        String mockResponse = "{\"success\":true,\"lowest_price\":\"$51.59\",\"median_price\":\"$52.00\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        com.fasterxml.jackson.databind.JsonNode jsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(objectMapper.readTree(mockResponse)).thenReturn(jsonNode);

        when(jsonNode.has("success")).thenReturn(true);
        com.fasterxml.jackson.databind.JsonNode successNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(jsonNode.get("success")).thenReturn(successNode);
        when(successNode.asBoolean()).thenReturn(true);
        when(jsonNode.has("lowest_price")).thenReturn(true);
        when(jsonNode.get("lowest_price")).thenReturn(mock(com.fasterxml.jackson.databind.node.TextNode.class));
        when(jsonNode.get("lowest_price").asText()).thenReturn("$51.59");

        // Act
        BigDecimal price = steamPriceService.fetchCurrentPrice("AK-47 | Redline (Field-Tested)");

        // Assert
        assertNotNull(price);
        assertEquals(new BigDecimal("47.46"), price);
    }

    @Test
    void testFetchCurrentPrice_ItemNotFound() throws Exception {
        // Arrange
        String mockResponse = "{\"success\":false}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        com.fasterxml.jackson.databind.JsonNode jsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(objectMapper.readTree(mockResponse)).thenReturn(jsonNode);

        when(jsonNode.has("success")).thenReturn(true);
        com.fasterxml.jackson.databind.JsonNode successNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(jsonNode.get("success")).thenReturn(successNode);
        when(successNode.asBoolean()).thenReturn(false);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            steamPriceService.fetchCurrentPrice("Invalid Item");
        });

        assertTrue(exception.getMessage().contains("Item not found"));
    }

    @Test
    void testFetchCurrentPrice_RateLimitError() {
        // Arrange
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", new org.springframework.http.HttpHeaders(), null, null));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            steamPriceService.fetchCurrentPrice("AK-47 | Redline (Field-Tested)");
        });

        assertTrue(exception.getMessage().contains("Too many requests"));
    }

    @Test
    void testValidateItemOnSteam_Success() throws Exception {
        // Arrange
        String mockResponse = "{\"success\":true,\"lowest_price\":\"$51.59\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        com.fasterxml.jackson.databind.JsonNode jsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(objectMapper.readTree(mockResponse)).thenReturn(jsonNode);

        when(jsonNode.has("success")).thenReturn(true);
        com.fasterxml.jackson.databind.JsonNode successNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(jsonNode.get("success")).thenReturn(successNode);
        when(successNode.asBoolean()).thenReturn(true);
        when(jsonNode.has("lowest_price")).thenReturn(true);
        when(jsonNode.get("lowest_price")).thenReturn(mock(com.fasterxml.jackson.databind.node.TextNode.class));
        when(jsonNode.get("lowest_price").asText()).thenReturn("$51.59");

        // Act
        boolean result = steamPriceService.validateItemOnSteam("AK-47 | Redline (Field-Tested)");

        // Assert
        assertTrue(result);
    }

    @Test
    void testValidateItemOnSteam_Failure() throws Exception {
        // Arrange
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        // Act
        boolean result = steamPriceService.validateItemOnSteam("Invalid Item");

        // Assert
        assertFalse(result);
    }

    @Test
    void testUpdatePricesForItems_Success() throws Exception {
        // Arrange
        String mockResponse = "{\"success\":true,\"lowest_price\":\"$55.00\"}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        com.fasterxml.jackson.databind.JsonNode jsonNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(objectMapper.readTree(mockResponse)).thenReturn(jsonNode);

        when(jsonNode.has("success")).thenReturn(true);
        com.fasterxml.jackson.databind.JsonNode successNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        when(jsonNode.get("success")).thenReturn(successNode);
        when(successNode.asBoolean()).thenReturn(true);
        when(jsonNode.has("lowest_price")).thenReturn(true);
        when(jsonNode.get("lowest_price")).thenReturn(mock(com.fasterxml.jackson.databind.node.TextNode.class));
        when(jsonNode.get("lowest_price").asText()).thenReturn("$55.00");

        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);
        when(priceHistoryRepository.save(any(PriceHistory.class))).thenReturn(new PriceHistory());

        // Act
        steamPriceService.updatePricesForItems(java.util.List.of(1L));

        // Assert
        verify(itemRepository, times(1)).save(any(Item.class));
        verify(priceHistoryRepository, times(1)).save(any(PriceHistory.class));
    }

    @Test
    void testUpdatePricesForItems_ItemNotFound() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        steamPriceService.updatePricesForItems(java.util.List.of(1L));

        // Assert
        verify(itemRepository, never()).save(any(Item.class));
        verify(priceHistoryRepository, never()).save(any(PriceHistory.class));
    }

    @Test
    void testUpdatePricesForItems_NoMarketHashName() {
        // Arrange
        testItem.setMarketHashName(null);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // Act
        steamPriceService.updatePricesForItems(java.util.List.of(1L));

        // Assert
        verify(itemRepository, never()).save(any(Item.class));
        verify(priceHistoryRepository, never()).save(any(PriceHistory.class));
    }
}
