package com.kalin.investment_tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.dto.PriceHistoryDto;
import com.kalin.investment_tracker.dto.SteamItemDto;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.PriceHistory;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.security.JwtService;
import com.kalin.investment_tracker.service.SteamPriceService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import com.kalin.investment_tracker.repository.CsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SteamPriceControllerTest {

    @Mock
    private SteamPriceService steamPriceService;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CsItemRepository csItemRepository;

    @InjectMocks
    private SteamPriceController steamPriceController;

    private Item testItem;
    private PriceHistory testPriceHistory;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1);

        testItem = new Item();
        testItem.setId(1L);
        testItem.setItemName("AK-47 | Redline (Field-Tested)");
        testItem.setMarketHashName("AK-47 | Redline (Field-Tested)");
        testItem.setQuantity(1);
        testItem.setPrice(new BigDecimal("50.00"));
        testItem.setBoughtPrice(new BigDecimal("45.00"));
        testItem.setShowInDashboard(true);
        testItem.setSteamFoundCheck(true);
        testItem.setUser(testUser);

        testPriceHistory = new PriceHistory();
        testPriceHistory.setId(1L);
        testPriceHistory.setItem(testItem);
        testPriceHistory.setPrice(new BigDecimal("50.00"));
        testPriceHistory.setQuantity(1);
        testPriceHistory.setTotalPrice(new BigDecimal("50.00"));
        testPriceHistory.setDateAssigned(LocalDateTime.now());
        testPriceHistory.setDateExtracted(LocalDateTime.now());
    }

    @Test
    void testGetAllItems_Success() {
        // Arrange
        when(itemRepository.findAll()).thenReturn(Arrays.asList(testItem));

        // Act
        ResponseEntity<List<SteamItemDto>> response = steamPriceController.getAllItems(null, false);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("AK-47 | Redline (Field-Tested)", response.getBody().get(0).getItemName());
    }

    @Test
    void testGetAllItems_WithToken() {
        // Arrange
        when(jwtService.extractUserId(anyString())).thenReturn("1");
        when(itemRepository.findAll()).thenReturn(Arrays.asList(testItem));

        // Act
        ResponseEntity<List<SteamItemDto>> response = steamPriceController.getAllItems("Bearer token", false);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testAddItem_Success() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder()
                .itemName("AK-47 | Redline (Field-Tested)")
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .quantity(1)
                .price(new BigDecimal("50.00"))
                .boughtPrice(new BigDecimal("45.00"))
                .showInDashboard(true)
                .build();

        when(steamPriceService.tryFetchPrice(anyString())).thenReturn(Optional.of(new BigDecimal("50.00")));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);
        when(jwtService.extractUserId(anyString())).thenReturn("1");
        when(priceHistoryRepository.save(any(PriceHistory.class))).thenReturn(testPriceHistory);

        // Act
        ResponseEntity<?> response = steamPriceController.addItem(itemDto, "Bearer token");

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testAddItem_MissingMarketHashName() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder()
                .itemName("Invalid Item")
                .marketHashName(null)
                .build();

        // Act
        ResponseEntity<?> response = steamPriceController.addItem(itemDto, "Bearer token");

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testUpdatePrices_Success() {
        // Arrange
        Map<String, List<Long>> request = Map.of("itemIds", Arrays.asList(1L, 2L));

        // Act
        ResponseEntity<?> response = steamPriceController.updatePrices(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void testUpdatePrices_NoItemIds() {
        // Arrange
        Map<String, List<Long>> request = Map.of("itemIds", Arrays.asList());

        // Act
        ResponseEntity<?> response = steamPriceController.updatePrices(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testGetPriceHistory_Success() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(priceHistoryRepository.findByItemIdOrderByDateExtractedDesc(1L))
                .thenReturn(Arrays.asList(testPriceHistory));

        // Act
        ResponseEntity<List<PriceHistoryDto>> response = steamPriceController.getPriceHistory(1L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(new BigDecimal("50.00"), response.getBody().get(0).getPrice());
    }

    @Test
    void testGetPriceHistory_ItemNotFound() {
        // Arrange
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<List<PriceHistoryDto>> response = steamPriceController.getPriceHistory(1L);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testUpdateItem_Success() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder()
                .itemName("Updated Name")
                .quantity(2)
                .price(new BigDecimal("60.00"))
                .build();

        when(itemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        // Act
        ResponseEntity<?> response = steamPriceController.updateItem(1L, itemDto);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testUpdateItem_ItemNotFound() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder().build();
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = steamPriceController.updateItem(1L, itemDto);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testDeleteItem_Success() {
        // Arrange
        doNothing().when(priceHistoryRepository).deleteByItemId(1L);
        doNothing().when(itemRepository).deleteById(1L);

        // Act
        ResponseEntity<?> response = steamPriceController.deleteItem(1L);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
    }

    @Test
    void testValidateItem_Success() {
        // Arrange
        Map<String, String> request = Map.of("marketHashName", "AK-47 | Redline (Field-Tested)");
        when(steamPriceService.validateItemOnSteam(anyString())).thenReturn(true);

        // Act
        ResponseEntity<?> response = steamPriceController.validateItem(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertTrue(body.containsKey("exists"));
        assertEquals(true, body.get("exists"));
    }

    @Test
    void testValidateItem_NoMarketHashName() {
        // Arrange
        Map<String, String> request = Map.of();

        // Act
        ResponseEntity<?> response = steamPriceController.validateItem(request);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testValidateItem_Failure() {
        // Arrange
        Map<String, String> request = Map.of("marketHashName", "Invalid Item");
        when(steamPriceService.validateItemOnSteam(anyString())).thenReturn(false);

        // Act
        ResponseEntity<?> response = steamPriceController.validateItem(request);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(false, body.get("exists"));
    }
}
