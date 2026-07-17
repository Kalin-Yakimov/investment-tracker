package com.kalin.investment_tracker.controller;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@CrossOrigin("http://localhost:3000")
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SteamPriceController {

    private final SteamPriceService steamPriceService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ItemRepository itemRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * GET /api/items - Get all items with Steam market data
     */
    @GetMapping("/items")
    public ResponseEntity<List<SteamItemDto>> getAllItems(
            @RequestHeader(value = "Authorization", required = false) String token) {
        try {
            List<Item> items;
            
            // If token provided, filter by user, otherwise return all (for admin/debug)
            if (token != null && !token.isEmpty()) {
                String jwt = token.substring(7);
                String userIdStr = jwtService.extractUserId(jwt);
                items = itemRepository.findAll().stream()
                        .filter(item -> item.getUser() != null && item.getUser().getId().equals(Integer.parseInt(userIdStr)))
                        .toList();
            } else {
                items = itemRepository.findAll();
            }

            List<SteamItemDto> dtos = items.stream()
                    .map(this::mapToSteamItemDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        } catch (Exception e) {
            log.error("Error fetching items: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/items - Add a new item with Steam market validation
     */
    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody SteamItemDto itemDto,
                                      @RequestHeader("Authorization") String token) {
        try {
            // Validate item exists on Steam market
            if (itemDto.getMarketHashName() != null && !itemDto.getMarketHashName().isEmpty()) {
                boolean existsOnSteam = steamPriceService.validateItemOnSteam(itemDto.getMarketHashName());
                if (!existsOnSteam) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Item not found on Steam Community Market"));
                }
            }

            // Create new item
            Item item = new Item();
            item.setItemName(itemDto.getItemName());
            item.setMarketHashName(itemDto.getMarketHashName());
            item.setCsItemId(itemDto.getCsItemId());
            item.setQuantity(itemDto.getQuantity());
            item.setPrice(itemDto.getPrice());
            item.setBoughtPrice(itemDto.getBoughtPrice());
            item.setBoughtDate(itemDto.getBoughtDate());
            item.setShowInDashboard(itemDto.getShowInDashboard() != null ? itemDto.getShowInDashboard() : false);
            item.setPriceNotification(itemDto.getPriceNotification());
            item.setSteamFoundCheck(true);

            // Set user from token
            String jwt = token.substring(7);
            String userIdStr = jwtService.extractUserId(jwt);
            User user = userRepository.findById(Integer.parseInt(userIdStr))
                    .orElseThrow(() -> new RuntimeException("User not found"));
            item.setUser(user);

            Item savedItem = itemRepository.save(item);

            // Create initial price history entry
            if (savedItem.getPrice() != null) {
                PriceHistory priceHistory = new PriceHistory(
                        savedItem,
                        savedItem.getPrice(),
                        savedItem.getQuantity(),
                        java.time.LocalDateTime.now(),
                        java.time.LocalDateTime.now()
                );
                priceHistoryRepository.save(priceHistory);
            }

            return ResponseEntity.ok(mapToSteamItemDto(savedItem));

        } catch (Exception e) {
            log.error("Error adding item: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add item: " + e.getMessage()));
        }
    }

    /**
     * POST /api/prices/update - Update prices for selected items (bulk)
     */
    @PostMapping("/prices/update")
    public ResponseEntity<?> updatePrices(@RequestBody Map<String, List<Long>> request) {
        try {
            List<Long> itemIds = request.get("itemIds");
            
            if (itemIds == null || itemIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No item IDs provided"));
            }

            // Run price update asynchronously
            new Thread(() -> {
                try {
                    steamPriceService.updatePricesForItems(itemIds);
                } catch (Exception e) {
                    log.error("Error in async price update: {}", e.getMessage());
                }
            }).start();

            return ResponseEntity.ok(Map.of(
                    "message", "Price update started for " + itemIds.size() + " items",
                    "itemCount", itemIds.size()
            ));

        } catch (Exception e) {
            log.error("Error starting price update: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to start price update: " + e.getMessage()));
        }
    }

    /**
     * GET /api/price-history/{itemId} - Get price history for charts
     */
    @GetMapping("/price-history/{itemId}")
    public ResponseEntity<List<PriceHistoryDto>> getPriceHistory(@PathVariable Long itemId) {
        try {
            Item item = itemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            List<PriceHistory> history = priceHistoryRepository.findByItemIdOrderByDateExtractedDesc(itemId);

            List<PriceHistoryDto> dtos = history.stream()
                    .map(ph -> PriceHistoryDto.builder()
                            .id(ph.getId())
                            .itemId(ph.getItem().getId())
                            .itemName(ph.getItem().getItemName())
                            .price(ph.getPrice())
                            .quantity(ph.getQuantity())
                            .totalPrice(ph.getTotalPrice())
                            .dateAssigned(ph.getDateAssigned())
                            .dateExtracted(ph.getDateExtracted())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);

        } catch (Exception e) {
            log.error("Error fetching price history for item {}: {}", itemId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * PUT /api/items/{id} - Update an existing item
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id,
                                        @RequestBody SteamItemDto itemDto) {
        try {
            Item item = itemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // Update fields
            if (itemDto.getItemName() != null) {
                item.setItemName(itemDto.getItemName());
            }
            if (itemDto.getMarketHashName() != null) {
                item.setMarketHashName(itemDto.getMarketHashName());
            }
            if (itemDto.getQuantity() > 0) {
                item.setQuantity(itemDto.getQuantity());
            }
            if (itemDto.getPrice() != null) {
                item.setPrice(itemDto.getPrice());
            }
            if (itemDto.getBoughtPrice() != null) {
                item.setBoughtPrice(itemDto.getBoughtPrice());
            }
            if (itemDto.getBoughtDate() != null) {
                item.setBoughtDate(itemDto.getBoughtDate());
            }
            if (itemDto.getShowInDashboard() != null) {
                item.setShowInDashboard(itemDto.getShowInDashboard());
            }
            if (itemDto.getPriceNotification() != null) {
                item.setPriceNotification(itemDto.getPriceNotification());
            }

            Item updatedItem = itemRepository.save(item);
            return ResponseEntity.ok(mapToSteamItemDto(updatedItem));

        } catch (Exception e) {
            log.error("Error updating item {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to update item: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/items/{id} - Delete an item
     */
    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable Long id) {
        try {
            // Delete price history first
            priceHistoryRepository.deleteByItemId(id);
            
            // Then delete the item
            itemRepository.deleteById(id);

            return ResponseEntity.ok(Map.of("message", "Item deleted successfully"));

        } catch (Exception e) {
            log.error("Error deleting item {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete item: " + e.getMessage()));
        }
    }

    /**
     * POST /api/prices/validate - Validate an item name on Steam market
     */
    @PostMapping("/prices/validate")
    public ResponseEntity<?> validateItem(@RequestBody Map<String, String> request) {
        try {
            String marketHashName = request.get("marketHashName");
            
            if (marketHashName == null || marketHashName.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "marketHashName is required"));
            }

            boolean exists = steamPriceService.validateItemOnSteam(marketHashName);
            
            return ResponseEntity.ok(Map.of(
                    "exists", exists,
                    "marketHashName", marketHashName
            ));

        } catch (Exception e) {
            log.error("Error validating item: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to validate item: " + e.getMessage()));
        }
    }

    private SteamItemDto mapToSteamItemDto(Item item) {
        return SteamItemDto.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .marketHashName(item.getMarketHashName())
                .csItemId(item.getCsItemId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .boughtPrice(item.getBoughtPrice())
                .boughtDate(item.getBoughtDate())
                .showInDashboard(item.getShowInDashboard())
                .priceNotification(item.getPriceNotification())
                .steamFoundCheck(item.getSteamFoundCheck())
                .build();
    }
}
