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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
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

    public static class ItemPriceData {
        public final BigDecimal currentPrice;
        public final BigDecimal price24h;
        public final BigDecimal price7d;
        public final BigDecimal price30d;
        public final BigDecimal price90d;

        public ItemPriceData(BigDecimal currentPrice, BigDecimal price24h, BigDecimal price7d, BigDecimal price30d, BigDecimal price90d) {
            this.currentPrice = currentPrice;
            this.price24h = price24h;
            this.price7d = price7d;
            this.price30d = price30d;
            this.price90d = price90d;
        }
    }

    private final ConcurrentHashMap<String, ItemPriceData> priceCache = new ConcurrentHashMap<>();
    private Instant lastCacheUpdate = null;
    private static final long CACHE_TTL_MS = 3600000; // 1 hour

    // Cached USD→EUR rate, refreshed together with the price cache (avoids one HTTP call per item)
    private BigDecimal cachedEurRate = null;
    private static final BigDecimal DEFAULT_EUR_RATE = new BigDecimal("0.92");
    private static final double PRICE_CHANGE_THRESHOLD_PCT = 0.5; // Only record history if price changed by ≥ 0.5%
    private final java.util.concurrent.atomic.AtomicBoolean isRefreshingCache = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Refreshes the Steam price cache asynchronously/non-blockingly from the CSGO Trader API if expired.
     */
    public void refreshPriceCacheIfNeeded() {
        if (lastCacheUpdate != null && (Instant.now().toEpochMilli() - lastCacheUpdate.toEpochMilli() < CACHE_TTL_MS)) {
            return;
        }

        // Non-blocking: if another thread is already refreshing, return immediately using existing stale cache
        if (!isRefreshingCache.compareAndSet(false, true)) {
            return;
        }

        try {
            log.info("Refreshing Steam price cache from csgotrader API...");
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    "https://prices.csgotrader.app/latest/steam.json",
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );

            if (response != null && response.getBody() != null && response.getBody().length > 0) {
                byte[] responseBytes = response.getBody();
                String json;
                List<String> contentEncoding = response.getHeaders().get("Content-Encoding");
                if (contentEncoding != null && contentEncoding.contains("gzip")) {
                    try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(responseBytes);
                         java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bais)) {
                        json = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
                    }
                } else {
                    json = new String(responseBytes, StandardCharsets.UTF_8);
                }

                JsonNode root = objectMapper.readTree(json);
                java.util.Map<String, ItemPriceData> tempCache = new java.util.HashMap<>();

                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = root.fields();
                while (fields.hasNext()) {
                    java.util.Map.Entry<String, JsonNode> entry = fields.next();
                    String marketHashName = entry.getKey();
                    JsonNode priceInfo = entry.getValue();

                    double val24h = (priceInfo.has("last_24h") && priceInfo.get("last_24h").isNumber()) ? priceInfo.get("last_24h").asDouble() : 0.0;
                    double val7d = (priceInfo.has("last_7d") && priceInfo.get("last_7d").isNumber()) ? priceInfo.get("last_7d").asDouble() : 0.0;
                    double val30d = (priceInfo.has("last_30d") && priceInfo.get("last_30d").isNumber()) ? priceInfo.get("last_30d").asDouble() : 0.0;
                    double val90d = (priceInfo.has("last_90d") && priceInfo.get("last_90d").isNumber()) ? priceInfo.get("last_90d").asDouble() : 0.0;

                    double currentVal = val24h > 0 ? val24h : val7d > 0 ? val7d : val30d > 0 ? val30d : val90d;

                    if (currentVal > 0) {
                        tempCache.put(marketHashName, new ItemPriceData(
                                BigDecimal.valueOf(currentVal),
                                val24h > 0 ? BigDecimal.valueOf(val24h) : null,
                                val7d > 0 ? BigDecimal.valueOf(val7d) : null,
                                val30d > 0 ? BigDecimal.valueOf(val30d) : null,
                                val90d > 0 ? BigDecimal.valueOf(val90d) : null
                        ));
                    }
                }

                priceCache.clear();
                priceCache.putAll(tempCache);
                lastCacheUpdate = Instant.now();
                log.info("Successfully loaded {} prices into cache", priceCache.size());

                // Also refresh the USD→EUR rate alongside the price cache (once per hour, not per item)
                cachedEurRate = fetchFreshUsdToEurRate();
                log.debug("USD→EUR rate refreshed alongside price cache: {}", cachedEurRate);
            }
        } catch (Exception e) {
            log.error("Failed to load prices from csgotrader API: {}", e.getMessage(), e);
            // Do not clear the cache on failure so we keep the stale data if available
        } finally {
            isRefreshingCache.set(false);
        }
    }

    /**
     * Fetches a fresh USD to EUR exchange rate from the API.
     * Falls back to a default rate of 0.92 if the API is down.
     */
    private BigDecimal fetchFreshUsdToEurRate() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity("https://open.er-api.com/v6/latest/USD", String.class);
            if (response != null && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("rates") && root.get("rates").has("EUR")) {
                    double eurRate = root.get("rates").get("EUR").asDouble();
                    if (eurRate > 0) {
                        log.debug("Fetched current USD to EUR rate: {}", eurRate);
                        return BigDecimal.valueOf(eurRate);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch USD to EUR rate, using default 0.92: {}", e.getMessage());
        }
        return DEFAULT_EUR_RATE;
    }

    /**
     * Returns the cached USD→EUR rate, initialising it on first call.
     * Prefer this over fetchFreshUsdToEurRate() to avoid per-item HTTP calls.
     */
    private BigDecimal getUsdToEurRate() {
        if (cachedEurRate == null) {
            cachedEurRate = fetchFreshUsdToEurRate();
        }
        return cachedEurRate;
    }

    /**
     * Gets item price data (current, 24h, 7d, 30d, 90d) converted to EUR.
     */
    public ItemPriceData getItemPriceData(String marketHashName) {
        refreshPriceCacheIfNeeded();
        ItemPriceData data = priceCache.get(marketHashName);
        if (data == null) {
            return null;
        }

        BigDecimal rate = getUsdToEurRate();
        BigDecimal currentEur = data.currentPrice.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal p24hEur = data.price24h != null ? data.price24h.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal p7dEur = data.price7d != null ? data.price7d.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal p30dEur = data.price30d != null ? data.price30d.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal p90dEur = data.price90d != null ? data.price90d.multiply(rate).setScale(2, java.math.RoundingMode.HALF_UP) : null;

        return new ItemPriceData(currentEur, p24hEur, p7dEur, p30dEur, p90dEur);
    }

    /**
     * Fetches current market price for a single item from Steam Community Market or CSGO Trader cache (converted to EUR)
     */
    public BigDecimal fetchCurrentPrice(String marketHashName) throws Exception {
        ItemPriceData data = getItemPriceData(marketHashName);
        if (data != null && data.currentPrice != null) {
            log.info("Price for '{}' found in cache: €{}", marketHashName, data.currentPrice);
            return data.currentPrice;
        }

        log.warn("Price for '{}' not found in cache. Falling back to Steam API...", marketHashName);
        String encodedName = URLEncoder.encode(marketHashName, StandardCharsets.UTF_8);
        String url = STEAM_PRICE_OVERVIEW_URL + encodedName;

        log.info("Fetching price for: {} from Steam API", marketHashName);
        long startTime = System.currentTimeMillis();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/javascript, */*; q=0.01");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Referer", "https://steamcommunity.com/market/");
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Connection", "keep-alive");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            log.info("Steam API response for {}: Status={}, Time={}ms", marketHashName, response.getStatusCode(), responseTime);
            log.info("Steam API response body for {}: {}", marketHashName, response.getBody());

            if (response.getBody() == null || response.getBody().isEmpty()) {
                throw new Exception("Empty response from Steam API");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            
            if (!root.has("success") || root.get("success").asBoolean() != true) {
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
            BigDecimal eurPrice = price.multiply(getUsdToEurRate()).setScale(2, java.math.RoundingMode.HALF_UP);
            log.info("Successfully fetched price for {}: ${} (converted to EUR: €{})", marketHashName, price, eurPrice);
            
            return eurPrice;

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
     * Updates prices for a list of items with rate limiting and batched DB writes
     */
    public void updatePricesForItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;

        int consecutiveErrors = 0;
        int successCount = 0;
        int errorCount = 0;

        log.info("Starting price update for {} items", itemIds.size());

        // Batch load all items in a single DB query instead of loop findById
        List<Item> itemsToUpdate = itemRepository.findAllById(itemIds);
        List<Item> modifiedItems = new java.util.ArrayList<>();
        List<PriceHistory> historiesToSave = new java.util.ArrayList<>();

        for (Item item : itemsToUpdate) {
            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                log.error("Stopping price update after {} consecutive errors", MAX_CONSECUTIVE_ERRORS);
                break;
            }

            try {
                if (item.getMarketHashName() == null || item.getMarketHashName().isEmpty()) {
                    log.warn("Item {} has no market hash name, skipping", item.getId());
                    continue;
                }

                // Rate limiting delay - only sleep if not in cache (i.e. hitting the real Steam API)
                if (successCount > 0 && !priceCache.containsKey(item.getMarketHashName())) {
                    int delay = new Random().nextInt(MAX_DELAY_MS - MIN_DELAY_MS + 1) + MIN_DELAY_MS;
                    log.debug("Rate limiting: waiting {}ms before next request", delay);
                    TimeUnit.MILLISECONDS.sleep(delay);
                }

                BigDecimal currentPrice = fetchCurrentPrice(item.getMarketHashName());

                // --- Deduplication: skip PriceHistory save if price hasn't changed meaningfully (< 0.5%) ---
                BigDecimal previousPrice = item.getPrice();
                boolean priceChangedSignificantly = true;
                if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                    double changePct = currentPrice.subtract(previousPrice).abs()
                            .divide(previousPrice, 6, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();
                    priceChangedSignificantly = changePct >= PRICE_CHANGE_THRESHOLD_PCT;
                    if (!priceChangedSignificantly) {
                        log.debug("Price for '{}' unchanged ({} → {}), skipping PriceHistory entry",
                                item.getMarketHashName(), previousPrice, currentPrice);
                    }
                }

                // Update item's current price
                item.setPrice(currentPrice);
                item.setSteamFoundCheck(true);
                modifiedItems.add(item);

                // Only write a PriceHistory row if the price moved meaningfully
                if (priceChangedSignificantly) {
                    historiesToSave.add(new PriceHistory(
                            item,
                            currentPrice,
                            item.getQuantity(),
                            LocalDateTime.now(),
                            LocalDateTime.now()
                    ));
                    log.info("Queued price update for item {}: €{} (history queued)", item.getId(), currentPrice);
                } else {
                    log.info("Queued price update for item {}: €{} (no history entry — price stable)", item.getId(), currentPrice);
                }
                successCount++;
                consecutiveErrors = 0;

            } catch (Exception e) {
                consecutiveErrors++;
                errorCount++;
                log.error("Error updating price for item {}: {}", item.getId(), e.getMessage());

                // Mark item as not found on Steam if appropriate
                if (e.getMessage() != null && e.getMessage().contains("not found")) {
                    item.setSteamFoundCheck(false);
                    modifiedItems.add(item);
                }
            }
        }

        // Execute batch database writes
        if (!modifiedItems.isEmpty()) {
            itemRepository.saveAll(modifiedItems);
        }
        if (!historiesToSave.isEmpty()) {
            priceHistoryRepository.saveAll(historiesToSave);
        }

        log.info("Price update completed. Success: {}, Errors: {}, Items Saved: {}, Histories Saved: {}",
                successCount, errorCount, modifiedItems.size(), historiesToSave.size());
    }

    /**
     * Updates prices for all tracked items (all items in the tracking system)
     * Uses a targeted DB query instead of findAll() + in-memory filtering for scalability.
     */
    public void updateAllTrackedItems() {
        List<Item> allTrackedItems = itemRepository.findAllSteamTrackedItems();

        if (allTrackedItems.isEmpty()) {
            log.info("No tracked items to update");
            return;
        }

        List<Long> itemIds = allTrackedItems.stream()
                .map(Item::getId)
                .toList();

        updatePricesForItems(itemIds);
    }

    /**
     * Updates prices for all items that have showInDashboard = true (bookmarked items)
     * Uses a targeted DB query instead of findAll() + in-memory filtering.
     */
    public void updateAllDashboardItems() {
        List<Item> dashboardItems = itemRepository.findAllSteamTrackedItems().stream()
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
     * Safe price fetch that never throws - returns Optional.empty() on any error.
     * Use this for best-effort fetches where failure is acceptable.
     */
    public java.util.Optional<BigDecimal> tryFetchPrice(String marketHashName) {
        try {
            BigDecimal price = fetchCurrentPrice(marketHashName);
            return java.util.Optional.of(price);
        } catch (Exception e) {
            log.warn("Best-effort price fetch failed for '{}': {}", marketHashName, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Checks if an item exists on Steam market.
     * Kept for compatibility — internally uses tryFetchPrice.
     */
    public boolean validateItemOnSteam(String marketHashName) {
        return tryFetchPrice(marketHashName).isPresent();
    }

    /**
     * Seeds synthetic historical PriceHistory rows for a newly added item.
     *
     * Because the Steam /pricehistory/ endpoint requires user authentication, we instead
     * use the 24h / 7d / 30d / 90d average prices that are already available in our
     * in-memory cache (sourced from csgotrader). Each average is written as a backdated
     * snapshot so the chart has meaningful data from day one rather than a single point.
     *
     * Rows are only created for periods where csgotrader actually has price data.
     * If the cache is empty (e.g. first startup) we skip seeding gracefully.
     *
     * @param savedItem the newly persisted Item entity
     * @param priceHistoryRepository repository to save the synthetic rows
     */
    public void seedHistoricalPriceData(Item savedItem,
                                        PriceHistoryRepository priceHistoryRepository) {
        refreshPriceCacheIfNeeded();
        ItemPriceData data = priceCache.get(savedItem.getMarketHashName());
        if (data == null) {
            log.debug("No cache data for '{}' — skipping historical seeding", savedItem.getMarketHashName());
            return;
        }

        BigDecimal rate = getUsdToEurRate();
        LocalDateTime now = LocalDateTime.now();

        // Build (offset-days → raw-USD-price) pairs for each available period
        java.util.LinkedHashMap<Integer, BigDecimal> periods = new java.util.LinkedHashMap<>();
        if (data.price90d != null) periods.put(90, data.price90d);
        if (data.price30d != null) periods.put(30, data.price30d);
        if (data.price7d  != null) periods.put(7,  data.price7d);
        if (data.price24h != null) periods.put(1,  data.price24h);

        if (periods.isEmpty()) {
            log.debug("No historical period data for '{}' — skipping seeding", savedItem.getMarketHashName());
            return;
        }

        java.util.List<PriceHistory> seedRows = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, BigDecimal> entry : periods.entrySet()) {
            int daysAgo = entry.getKey();
            BigDecimal eurPrice = entry.getValue()
                    .multiply(rate)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            LocalDateTime timestamp = now.minusDays(daysAgo);
            seedRows.add(new PriceHistory(
                    savedItem,
                    eurPrice,
                    savedItem.getQuantity(),
                    timestamp,
                    timestamp
            ));
        }

        priceHistoryRepository.saveAll(seedRows);
        log.info("Seeded {} historical price points for new item '{}'",
                seedRows.size(), savedItem.getMarketHashName());
    }
}
