package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.PriceHistoryDto;
import com.kalin.investment_tracker.dto.SteamItemDto;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.PriceHistory;
import com.kalin.investment_tracker.repository.CsItemRepository;
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

import java.math.BigDecimal;
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
    private final CsItemRepository csItemRepository;

    /**
     * GET /api/items - Get all items with Steam market data
     * Query param: dashboard=true to get only bookmarked items
     */
    @GetMapping("/items")
    public ResponseEntity<List<SteamItemDto>> getAllItems(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "dashboard", required = false, defaultValue = "false") boolean dashboard) {
        try {
            List<Item> items;
            
            // If token provided, filter by user, otherwise return all (for admin/debug)
            if (token != null && !token.isEmpty()) {
                String jwt = token.substring(7);
                String userIdStr = jwtService.extractUserId(jwt);
                items = itemRepository.findAll().stream()
                        .filter(item -> item.getUser() != null && item.getUser().getId().equals(Integer.parseInt(userIdStr)))
                        .filter(item -> Boolean.TRUE.equals(item.getSteamFoundCheck()))
                        .toList();
            } else {
                items = itemRepository.findAll().stream()
                        .filter(item -> Boolean.TRUE.equals(item.getSteamFoundCheck()))
                        .toList();
            }

            // Filter by dashboard status if requested
            if (dashboard) {
                items = items.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getShowInDashboard()))
                        .toList();
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
            // If csItemId is provided, pull marketHashName and name directly from our DB
            if (itemDto.getCsItemId() != null && !itemDto.getCsItemId().isEmpty()) {
                com.kalin.investment_tracker.model.CsItem csItem = csItemRepository.findById(itemDto.getCsItemId())
                        .orElse(null);
                if (csItem != null) {
                    // DB record is authoritative - always override with DB market hash name
                    itemDto.setMarketHashName(csItem.getMarketHashName());
                    if (itemDto.getItemName() == null || itemDto.getItemName().isEmpty()) {
                        itemDto.setItemName(csItem.getName());
                    }
                }
            }

            if (itemDto.getMarketHashName() == null || itemDto.getMarketHashName().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "marketHashName is required"));
            }

            // Set user from token
            String jwt = token.substring(7);
            String userIdStr = jwtService.extractUserId(jwt);
            User user = userRepository.findById(Integer.parseInt(userIdStr))
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check if this exact item is already being tracked by this user
            boolean alreadyTracked = itemRepository.findAll().stream()
                    .anyMatch(i -> i.getUser() != null
                            && i.getUser().getId().equals(user.getId())
                            && itemDto.getMarketHashName().equals(i.getMarketHashName())
                            && Boolean.TRUE.equals(i.getSteamFoundCheck()));
            if (alreadyTracked) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "You are already tracking this item"));
            }

            // Create new tracking item — no blocking Steam validation
            Item item = new Item();
            item.setItemName(itemDto.getItemName());
            item.setMarketHashName(itemDto.getMarketHashName());
            item.setCsItemId(itemDto.getCsItemId());
            item.setQuantity(itemDto.getQuantity() > 0 ? itemDto.getQuantity() : 1);
            item.setShowInDashboard(itemDto.getShowInDashboard() != null ? itemDto.getShowInDashboard() : false);
            item.setPriceNotification(itemDto.getPriceNotification());
            // Mark as steam-tracked immediately — the item exists in our DB from the official CSGO API
            item.setSteamFoundCheck(true);
            item.setUser(user);

            // Best-effort: try to fetch current price from Steam
            // If Steam is rate-limiting or unavailable, save anyway — scheduler will retry in 30min
            java.util.Optional<java.math.BigDecimal> maybePriceOpt = steamPriceService.tryFetchPrice(item.getMarketHashName());
            maybePriceOpt.ifPresent(item::setPrice);

            Item savedItem = itemRepository.save(item);
            log.info("Item '{}' saved for user {} (price={})",
                    savedItem.getMarketHashName(), user.getId(),
                    savedItem.getPrice() != null ? savedItem.getPrice() : "pending scheduler");

            // Save initial price history entry if we got a price
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

            // Build response DTO and attach image from cs_items if available
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
     * PUT /api/items/{id} - Update an existing item (mainly for bookmark toggle)
     */
    @PutMapping("/items/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id,
                                        @RequestBody SteamItemDto itemDto) {
        try {
            Item item = itemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // Only update bookmark status and price notification for tracking
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
     * PATCH /api/items/{id}/bookmark - Toggle bookmark status
     */
    @PatchMapping("/items/{id}/bookmark")
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id,
                                           @RequestHeader("Authorization") String token) {
        try {
            Item item = itemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            // Verify ownership
            String jwt = token.substring(7);
            String userIdStr = jwtService.extractUserId(jwt);
            if (!item.getUser().getId().equals(Integer.parseInt(userIdStr))) {
                return ResponseEntity.status(403)
                        .body(Map.of("error", "You are not authorized to modify this item"));
            }

            // Toggle bookmark status
            item.setShowInDashboard(!item.getShowInDashboard());
            Item updatedItem = itemRepository.save(item);

            return ResponseEntity.ok(Map.of(
                    "bookmarked", updatedItem.getShowInDashboard(),
                    "item", mapToSteamItemDto(updatedItem)
            ));

        } catch (Exception e) {
            log.error("Error toggling bookmark for item {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to toggle bookmark: " + e.getMessage()));
        }
    }

    /**
     * DELETE /api/items/{id} - Delete an item
     */
    @DeleteMapping("/items/{id}")
    @org.springframework.transaction.annotation.Transactional
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
        SteamItemDto.SteamItemDtoBuilder builder = SteamItemDto.builder()
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
                .steamFoundCheck(item.getSteamFoundCheck());

        // Add image URL if csItemId is available
        if (item.getCsItemId() != null) {
            csItemRepository.findById(item.getCsItemId()).ifPresent(csItem -> {
                builder.image(csItem.getImage());
            });
        }

        // Add historical trend data if available
        if (item.getMarketHashName() != null) {
            SteamPriceService.ItemPriceData priceData = steamPriceService.getItemPriceData(item.getMarketHashName());
            if (priceData != null) {
                builder.price24h(priceData.price24h);
                builder.price7d(priceData.price7d);
                builder.price30d(priceData.price30d);
                builder.price90d(priceData.price90d);
            }
        }

        return builder.build();
    }

    /**
     * GET /api/items/{id}/details - Get item details with image
     */
    @GetMapping("/items/{id}/details")
    public ResponseEntity<Map<String, Object>> getItemDetails(@PathVariable Long id) {
        try {
            Item item = itemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Item not found"));

            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("id", item.getId());
            details.put("itemName", item.getItemName());
            details.put("marketHashName", item.getMarketHashName());
            details.put("csItemId", item.getCsItemId());
            details.put("quantity", item.getQuantity());
            details.put("price", item.getPrice());
            details.put("showInDashboard", item.getShowInDashboard());
            details.put("priceNotification", item.getPriceNotification());
            details.put("steamFoundCheck", item.getSteamFoundCheck());

            // Add image if available
            if (item.getCsItemId() != null) {
                csItemRepository.findById(item.getCsItemId()).ifPresent(csItem -> {
                    details.put("image", csItem.getImage());
                });
            }

            return ResponseEntity.ok(details);

        } catch (Exception e) {
            log.error("Error fetching item details for {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/cs-items/search - Search CS items by name
     * Returns items with their market hash names for Steam integration
     */
    @GetMapping("/cs-items/search")
    public ResponseEntity<List<Map<String, Object>>> searchCsItems(@RequestParam String query) {
        try {
            List<com.kalin.investment_tracker.model.CsItem> csItems = csItemRepository
                    .findAll(com.kalin.investment_tracker.repository.CsItemSpecification.containsTextInName(query),
                            org.springframework.data.domain.PageRequest.of(0, 10))
                    .getContent();

            List<Map<String, Object>> result = csItems.stream()
                    .map(csItem -> {
                        Map<String, Object> itemMap = new java.util.HashMap<>();
                        itemMap.put("id", csItem.getId());
                        itemMap.put("name", csItem.getName());
                        itemMap.put("marketHashName", csItem.getMarketHashName());
                        itemMap.put("image", csItem.getImage());
                        return itemMap;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error searching CS items: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
