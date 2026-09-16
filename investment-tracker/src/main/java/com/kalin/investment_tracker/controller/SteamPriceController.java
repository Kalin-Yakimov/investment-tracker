package com.kalin.investment_tracker.controller;

import com.kalin.investment_tracker.dto.MarketPriceSnapshotDto;
import com.kalin.investment_tracker.dto.SteamItemDto;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.repository.CsItemRepository;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.service.SteamPriceService;
import com.kalin.investment_tracker.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class SteamPriceController {

    private final SteamPriceService steamPriceService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final ItemRepository itemRepository;
    private final CsItemRepository csItemRepository;
    private final TaskExecutor applicationTaskExecutor;

    /**
     * GET /api/items - Get all items with Steam market data
     * Query param: dashboard=true to get only bookmarked items
     */
    @GetMapping("/items")
    public ResponseEntity<List<SteamItemDto>> getAllItems(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "dashboard", required = false, defaultValue = "false") boolean dashboard) {
        try {
            List<Item> items = itemRepository.findSteamTrackedItemsByUserId(user.getId());

            // Filter by dashboard/pinned status if requested
            if (dashboard) {
                items = items.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getShowInDashboard()))
                        .toList();
            }

            // Bulk fetch all image URLs for items on this list in 1 query (eliminates N+1 DB calls)
            java.util.Set<String> csItemIds = items.stream()
                    .map(Item::getCsItemId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, String> imageMap = new java.util.HashMap<>();
            if (!csItemIds.isEmpty()) {
                imageMap = csItemRepository.findAllById(csItemIds).stream()
                        .collect(Collectors.toMap(
                                com.kalin.investment_tracker.model.CsItem::getId,
                                com.kalin.investment_tracker.model.CsItem::getImage,
                                (existing, replacement) -> existing
                        ));
            }

            final Map<String, String> finalImageMap = imageMap;
            List<SteamItemDto> dtos = items.stream()
                    .map(item -> mapToSteamItemDto(item, finalImageMap.get(item.getCsItemId())))
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
                                      @AuthenticationPrincipal User user) {
        try {
            // If csItemId is provided, pull marketHashName and name directly from DB
            if (itemDto.getCsItemId() != null && !itemDto.getCsItemId().isEmpty()) {
                com.kalin.investment_tracker.model.CsItem csItem = csItemRepository.findById(itemDto.getCsItemId())
                        .orElse(null);
                if (csItem != null) {
                    itemDto.setMarketHashName(csItem.getMarketHashName());
                    if (itemDto.getItemName() == null || itemDto.getItemName().isEmpty()) {
                        itemDto.setItemName(csItem.getName());
                    }
                }
            } else if (itemDto.getMarketHashName() != null && !itemDto.getMarketHashName().isEmpty()) {
                // Fallback resolution: find best matching CS item in DB if csItemId wasn't passed directly
                List<com.kalin.investment_tracker.model.CsItem> matches = csItemRepository.findBestMatch(
                        itemDto.getMarketHashName(), org.springframework.data.domain.PageRequest.of(0, 1));
                if (!matches.isEmpty()) {
                    com.kalin.investment_tracker.model.CsItem csItem = matches.get(0);
                    itemDto.setCsItemId(csItem.getId());
                    itemDto.setMarketHashName(csItem.getMarketHashName());
                    itemDto.setItemName(csItem.getName());
                }
            }

            if (itemDto.getMarketHashName() == null || itemDto.getMarketHashName().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "marketHashName is required"));
            }

            // Fast DB-level check if this exact item is already being tracked by this user
            boolean alreadyTracked = itemRepository.existsByUserIdAndMarketHashNameAndSteamFoundCheckTrue(
                    user.getId(), itemDto.getMarketHashName());
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

            // Best-effort: use the latest CSGOTrader 24-hour average from the shared feed.
            java.util.Optional<java.math.BigDecimal> maybePriceOpt = steamPriceService.tryFetchPrice(item.getMarketHashName());
            maybePriceOpt.ifPresent(item::setPrice);

            Item savedItem = itemRepository.save(item);
            log.info("Item '{}' saved for user {} (price={})",
                    savedItem.getMarketHashName(), user.getId(),
                    savedItem.getPrice() != null ? savedItem.getPrice() : "pending scheduler");

            // Record only the actual feed observation available now. No rolling average
            // is backdated and presented as a historical point.
            steamPriceService.recordCurrentSnapshotForItem(savedItem);

            // Build response DTO and attach image from cs_items if available
            return ResponseEntity.ok(mapToSteamItemDto(savedItem));

        } catch (Exception e) {
            log.error("Error adding item: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to add item: " + e.getMessage()));
        }
    }

    /**
     * POST /api/prices/update - Update prices for selected items (bulk).
     * Uses a Spring-managed thread pool via TaskExecutor instead of raw new Thread().
     */
    @PostMapping("/prices/update")
    public ResponseEntity<?> updatePrices(
            @RequestBody Map<String, List<Long>> request,
            @AuthenticationPrincipal User user) {
        try {
            List<Long> itemIds = request.get("itemIds");

            if (itemIds == null || itemIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No item IDs provided"));
            }

            List<Long> distinctItemIds = itemIds.stream().distinct().toList();
            List<Item> ownedItems = itemRepository
                    .findAllByIdInAndUserIdAndSteamFoundCheckTrue(distinctItemIds, user.getId());
            if (ownedItems.size() != distinctItemIds.size()) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "One or more tracked items were not found"));
            }
            List<Long> ownedItemIds = ownedItems.stream().map(Item::getId).toList();

            applicationTaskExecutor.execute(() -> {
                try {
                    steamPriceService.updatePricesForItems(ownedItemIds);
                } catch (Exception e) {
                    log.error("Error in async price update: {}", e.getMessage());
                }
            });

            return ResponseEntity.ok(Map.of(
                    "message", "Price update started for " + ownedItemIds.size() + " items",
                    "itemCount", ownedItemIds.size()
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
    public ResponseEntity<List<MarketPriceSnapshotDto>> getPriceHistory(
            @PathVariable Long itemId,
            @AuthenticationPrincipal User user) {
        try {
            Item item = itemRepository.findByIdAndUserId(itemId, user.getId()).orElse(null);
            if (item == null) {
                return ResponseEntity.notFound().build();
            }

            java.time.Instant cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(120));
            List<MarketPriceSnapshot> history = marketPriceSnapshotRepository
                    .findByMarketHashNameAndSourceAndObservedAtAfterOrderByObservedAtAsc(
                            item.getMarketHashName(),
                            MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                            cutoff
                    );

            List<MarketPriceSnapshotDto> dtos = history.stream()
                    .map(snapshot -> MarketPriceSnapshotDto.builder()
                            .id(snapshot.getId())
                            .price(snapshot.getPriceEur())
                            .dateExtracted(snapshot.getObservedAt())
                            .source(snapshot.getSource())
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
                                        @RequestBody SteamItemDto itemDto,
                                        @AuthenticationPrincipal User user) {
        try {
            Item item = itemRepository.findByIdAndUserId(id, user.getId()).orElse(null);
            if (item == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Tracked item not found"));
            }

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
     * PATCH /api/items/{id}/bookmark - Toggle bookmark status with IDOR protection
     */
    @PatchMapping("/items/{id}/bookmark")
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id,
                                           @AuthenticationPrincipal User user) {
        try {
            Item item = itemRepository.findByIdAndUserId(id, user.getId()).orElse(null);
            if (item == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Tracked item not found"));
            }

            // Toggle bookmark status
            item.setShowInDashboard(!Boolean.TRUE.equals(item.getShowInDashboard()));
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
     * DELETE /api/items/{id} - Delete an item with IDOR protection
     */
    @DeleteMapping("/items/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteItem(@PathVariable Long id,
                                         @AuthenticationPrincipal User user) {
        try {
            Item item = itemRepository.findByIdAndUserId(id, user.getId()).orElse(null);
            if (item == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Tracked item not found"));
            }

            // Delete price history first
            priceHistoryRepository.deleteByItemId(item.getId());
            
            // Then delete the item
            itemRepository.delete(item);

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
        String imageUrl = null;
        if (item.getCsItemId() != null) {
            imageUrl = csItemRepository.findById(item.getCsItemId())
                    .map(com.kalin.investment_tracker.model.CsItem::getImage)
                    .orElse(null);
        }
        return mapToSteamItemDto(item, imageUrl);
    }

    private SteamItemDto mapToSteamItemDto(Item item, String imageUrl) {
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
                .steamFoundCheck(item.getSteamFoundCheck())
                .image(imageUrl);

        // The current displayed value is the latest CSGOTrader 24-hour average.
        if (item.getMarketHashName() != null) {
            SteamPriceService.ItemPriceData priceData = steamPriceService.getItemPriceData(item.getMarketHashName());
            if (priceData != null) {
                builder.price(priceData.currentPrice);
                builder.price24h(priceData.price24h);
            }
        }

        return builder.build();
    }

    /**
     * GET /api/items/{id}/details - Get item details with image
     */
    @GetMapping("/items/{id}/details")
    public ResponseEntity<Map<String, Object>> getItemDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        try {
            Item item = itemRepository.findByIdAndUserId(id, user.getId()).orElse(null);
            if (item == null) {
                return ResponseEntity.notFound().build();
            }

            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("id", item.getId());
            details.put("itemName", item.getItemName());
            details.put("marketHashName", item.getMarketHashName());
            details.put("csItemId", item.getCsItemId());
            details.put("quantity", item.getQuantity());
            SteamPriceService.ItemPriceData priceData = item.getMarketHashName() == null
                    ? null
                    : steamPriceService.getItemPriceData(item.getMarketHashName());
            details.put("price", priceData != null ? priceData.currentPrice : item.getPrice());
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
