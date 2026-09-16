package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.MarketPriceSnapshotDto;
import com.kalin.investment_tracker.dto.SteamItemDto;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.model.PriceHistory;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.service.SteamPriceService;
import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.repository.CsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskExecutor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
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
    private MarketPriceSnapshotRepository marketPriceSnapshotRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private CsItemRepository csItemRepository;

    @Mock
    private TaskExecutor applicationTaskExecutor;

    @InjectMocks
    private SteamPriceController steamPriceController;

    private Item testItem;
    private PriceHistory testPriceHistory;
    private MarketPriceSnapshot testMarketSnapshot;
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

        testMarketSnapshot = new MarketPriceSnapshot(
                testItem.getMarketHashName(),
                MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                "feed-version-1",
                Instant.now(),
                new BigDecimal("54.35"),
                new BigDecimal("0.92"),
                new BigDecimal("50.00")
        );
        testMarketSnapshot.setId(10L);
    }

    @Test
    void testGetAllItems_Success() {
        // Arrange
        when(itemRepository.findSteamTrackedItemsByUserId(1)).thenReturn(Arrays.asList(testItem));

        // Act
        ResponseEntity<List<SteamItemDto>> response = steamPriceController.getAllItems(testUser, false);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("AK-47 | Redline (Field-Tested)", response.getBody().get(0).getItemName());
    }

    @Test
    void testGetAllItems_UsesFreshCachePriceAfterRestart() {
        when(itemRepository.findSteamTrackedItemsByUserId(1)).thenReturn(List.of(testItem));
        when(steamPriceService.getItemPriceData(testItem.getMarketHashName()))
                .thenReturn(new SteamPriceService.ItemPriceData(new BigDecimal("52.75")));

        ResponseEntity<List<SteamItemDto>> response = steamPriceController.getAllItems(testUser, false);

        assertNotNull(response.getBody());
        assertEquals(new BigDecimal("52.75"), response.getBody().getFirst().getPrice());
        assertEquals(new BigDecimal("52.75"), response.getBody().getFirst().getPrice24h());
    }

    @Test
    void testGetAllItems_NeverUsesGlobalItemQuery() {
        when(itemRepository.findSteamTrackedItemsByUserId(1)).thenReturn(List.of());

        steamPriceController.getAllItems(testUser, false);

        verify(itemRepository).findSteamTrackedItemsByUserId(1);
        verify(itemRepository, never()).findAllSteamTrackedItems();
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
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);
        // Act
        ResponseEntity<?> response = steamPriceController.addItem(itemDto, testUser);

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
        ResponseEntity<?> response = steamPriceController.addItem(itemDto, testUser);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testUpdatePrices_Success() {
        // Arrange
        Map<String, List<Long>> request = Map.of("itemIds", Arrays.asList(1L, 2L));
        Item secondItem = new Item();
        secondItem.setId(2L);
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(1L, 2L), 1))
                .thenReturn(List.of(testItem, secondItem));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(applicationTaskExecutor).execute(any(Runnable.class));

        // Act
        ResponseEntity<?> response = steamPriceController.updatePrices(request, testUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        verify(steamPriceService).updatePricesForItems(List.of(1L, 2L));
    }

    @Test
    void testUpdatePrices_NoItemIds() {
        // Arrange
        Map<String, List<Long>> request = Map.of("itemIds", Arrays.asList());

        // Act
        ResponseEntity<?> response = steamPriceController.updatePrices(request, testUser);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("error"));
    }

    @Test
    void testUpdatePrices_DeniesIdsThatDoNotBelongToUser() {
        Map<String, List<Long>> request = Map.of("itemIds", List.of(99L));
        when(itemRepository.findAllByIdInAndUserIdAndSteamFoundCheckTrue(List.of(99L), 1))
                .thenReturn(List.of());

        ResponseEntity<?> response = steamPriceController.updatePrices(request, testUser);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(applicationTaskExecutor, never()).execute(any(Runnable.class));
        verify(steamPriceService, never()).updatePricesForItems(anyList());
    }

    @Test
    void testGetPriceHistory_Success() {
        // Arrange
        when(itemRepository.findByIdAndUserId(1L, 1)).thenReturn(Optional.of(testItem));
        when(marketPriceSnapshotRepository.findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                eq(testItem.getMarketHashName()),
                eq(MarketPriceSnapshot.SOURCE_CSGOTRADER_24H),
                any(Instant.class)
        )).thenReturn(List.of(testMarketSnapshot));

        // Act
        ResponseEntity<List<MarketPriceSnapshotDto>> response = steamPriceController.getPriceHistory(1L, testUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(new BigDecimal("50.00"), response.getBody().get(0).getPrice());
    }

    @Test
    void testGetPriceHistory_ItemNotFound() {
        // Arrange
        when(itemRepository.findByIdAndUserId(1L, 1)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<List<MarketPriceSnapshotDto>> response = steamPriceController.getPriceHistory(1L, testUser);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(marketPriceSnapshotRepository, never())
                .findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(anyString(), anyString(), any());
    }

    @Test
    void testUpdateItem_Success() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder()
                .itemName("Updated Name")
                .quantity(2)
                .price(new BigDecimal("60.00"))
                .build();

        when(itemRepository.findByIdAndUserId(1L, 1)).thenReturn(Optional.of(testItem));
        when(itemRepository.save(any(Item.class))).thenReturn(testItem);

        // Act
        ResponseEntity<?> response = steamPriceController.updateItem(1L, itemDto, testUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testUpdateItem_ItemNotFound() {
        // Arrange
        SteamItemDto itemDto = SteamItemDto.builder().build();
        when(itemRepository.findByIdAndUserId(1L, 1)).thenReturn(Optional.empty());

        // Act
        ResponseEntity<?> response = steamPriceController.updateItem(1L, itemDto, testUser);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(itemRepository, never()).save(any(Item.class));
    }

    @Test
    void testDeleteItem_Success() {
        // Arrange
        when(itemRepository.findByIdAndUserId(1L, 1)).thenReturn(Optional.of(testItem));
        doNothing().when(priceHistoryRepository).deleteByItemId(1L);
        doNothing().when(itemRepository).delete(testItem);

        // Act
        ResponseEntity<?> response = steamPriceController.deleteItem(1L, testUser);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        verify(priceHistoryRepository).deleteByItemId(1L);
        verify(itemRepository).delete(testItem);
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
