package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.PriceHistory;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamPriceService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ItemRepository itemRepository;

    private static final String STEAM_PRICE_OVERVIEW_URL = "https://steamcommunity.com/market/priceoverview/?appid=730&currency=1&market_hash_name=";
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private static final int MIN_DELAY_MS = 1000;
    private static final int MAX_DELAY_MS = 5000;

    /**
     * Fetches current market price for a single item from Steam Community Market
     */
    public BigDecimal fetchCurrentPrice(String marketHashName) throws Exception {
        String encodedName = URLEncoder.encode(marketHashName, StandardCharsets.UTF_8);
        String url = STEAM_PRICE_OVERVIEW_URL + encodedName;

        log.info("Fetching price for: {} from Steam API", marketHashName);
        long startTime = System.currentTimeMillis();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("Steam API response for {}: Status={}, Time={}ms", marketHashName, response.getStatusCode(), responseTime);

            if (response.getBody() == null || response.getBody().isEmpty()) {
                throw new Exception("Empty response from Steam API");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (!root.has("success") || root.get("success").asInt() != 1) {
                log.warn("Steam API returned unsuccessful for item: {}", marketHashName);
                throw new Exception("Item not found on Steam market");
            }

            String lowestPrice = root.has("lowest_price") ? root.get("lowest_price").asText() : null;
            String medianPrice = root.has("median_price") ? root.get("median_price").asText() : null;

            // Prefer median price, fallback to lowest price
            String priceString = (medianPrice != null && !medianPrice.isEmpty()) ? medianPrice : lowestPrice;

            if (priceString == null || priceString.isEmpty()) {
                throw new Exception("No price data available for item: " + marketHashName);
            }

            BigDecimal price = parsePriceString(priceString);
            log.info("Successfully fetched price for {}: ${}", marketHashName, price);
            
            return price;

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Rate limit hit for item: {}. Status: {}", marketHashName, e.getStatusCode());
            throw new Exception("Too many requests to Steam API", e);
        } catch (ResourceAccessException e) {
            log.error("Timeout or connection error for item: {}", marketHashName, e);
            throw new Exception("Connection timeout to Steam API", e);
        } catch (Exception e) {
            log.error("Error fetching price for item: {}", marketHashName, e);
            throw e;
        }
    }

    /**
     * Parses price string from Steam API (e.g., "$51.59") to BigDecimal
     */
    private BigDecimal parsePriceString(String priceString) {
        // Remove currency symbol and any whitespace
        String cleaned = priceString.replaceAll("[^0-9.]", "");
        
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Invalid price string: " + priceString);
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse price: " + priceString, e);
        }
    }

    /**
     * Updates prices for a list of items with rate limiting
     */
    public void updatePricesForItems(List<Long> itemIds) {
        int consecutiveErrors = 0;
        int successCount = 0;
        int errorCount = 0;

        log.info("Starting price update for {} items", itemIds.size());

        for (Long itemId : itemIds) {
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                log.error("Stopping price update after {} consecutive errors", MAX_CONSECUTIVE_ERRORS);
                break;
            }

            try {
                Item item = itemRepository.findById(itemId)
                        .orElseThrow(() -> new Exception("Item not found with id: " + itemId));

                if (item.getMarketHashName() == null || item.getMarketHashName().isEmpty()) {
                    log.warn("Item {} has no market hash name, skipping", itemId);
                    continue;
                }

                // Rate limiting delay
                if (successCount > 0) {
                    int delay = new Random().nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1) + MIN_DELAY_MS;
                    log.debug("Rate limiting: waiting {}ms before next request", delay);
                    TimeUnit.MILLISECONDS.sleep(delay);
                }

                BigDecimal currentPrice = fetchCurrentPrice(item.getMarketHashName());
                
                // Update item's current price
                item.setPrice(currentPrice);
                item.setSteamFoundCheck(true);
                itemRepository.save(item);

                // Create price history entry
                PriceHistory priceHistory = new PriceHistory(
                        item,
                        currentPrice,
                        item.getQuantity(),
                        LocalDateTime.now(),
                        LocalDateTime.now()
                );
                priceHistoryRepository.save(priceHistory);

                log.info("Successfully updated price for item {}: ${}", itemId, currentPrice);
                successCount++;
                consecutiveErrors = 0;

            } catch (Exception e) {
                consecutiveErrors++;
                errorCount++;
                log.error("Error updating price for item {}: {}", itemId, e.getMessage());

                // Mark item as not found on Steam if appropriate
                try {
                    Item item = itemRepository.findById(itemId).orElse(null);
                    if (item != null && e.getMessage().contains("not found")) {
                        item.setSteamFoundCheck(false);
                        itemRepository.save(item);
                    }
                } catch (Exception ex) {
                    log.error("Error marking item as not found: {}", ex.getMessage());
                }
            }
        }

        log.info("Price update completed. Success: {}, Errors: {}", successCount, errorCount);
    }

    /**
     * Updates prices for all items that have showInDashboard = true
     */
    public void updateAllDashboardItems() {
        List<Item> dashboardItems = itemRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getShowInDashboard()))
                .toList();

        if (dashboardItems.isEmpty()) {
            log.info("No dashboard items to update");
            return;
        }

        List<Long> itemIds = dashboardItems.stream()
                .map(Item::getId)
                .toList();

        updatePricesForItems(itemIds);
    }

    /**
     * Checks if an item exists on Steam market (validation before adding)
     */
    public boolean validateItemOnSteam(String marketHashName) {
        try {
            fetchCurrentPrice(marketHashName);
            return true;
        } catch (Exception e) {
            log.warn("Item validation failed for {}: {}", marketHashName, e.getMessage());
            return false;
        }
    }
}
