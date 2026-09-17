package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.model.Item;
import com.kalin.investment_tracker.model.MarketPriceSnapshot;
import com.kalin.investment_tracker.repository.ItemRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamPriceService {

    private static final String CSGOTRADER_STEAM_PRICES_URL =
            "https://prices.csgotrader.app/latest/steam.json";
    private static final BigDecimal DEFAULT_EUR_RATE = new BigDecimal("0.92");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final ItemRepository itemRepository;

    private final ConcurrentHashMap<String, ItemPriceData> priceCache = new ConcurrentHashMap<>();
    private final AtomicBoolean isRefreshingCache = new AtomicBoolean(false);
    private final Object snapshotWriteLock = new Object();

    private volatile Instant lastCacheCheck;
    private volatile String lastFeedEtag;
    private volatile long lastFeedModifiedMillis = -1L;
    private volatile String lastFeedVersion;
    private volatile Instant lastFeedObservedAt;
    private volatile BigDecimal cachedEurRate;

    @Value("${price.feed.check-interval-ms:7200000}")
    private long feedCheckIntervalMs;

    public static class ItemPriceData {
        public final BigDecimal currentPrice;
        public final BigDecimal price24h;

        public ItemPriceData(BigDecimal price24h) {
            this.currentPrice = price24h;
            this.price24h = price24h;
        }
    }

    /**
     * Checks the shared CSGOTrader feed and loads it only when its ETag or
     * Last-Modified value indicates a new version.
     */
    public void refreshPriceCacheIfNeeded() {
        refreshPriceCache(false);
    }

    /**
     * Immediately checks CSGOTrader, bypassing the local time interval while
     * still using conditional HTTP headers to avoid downloading unchanged data.
     */
    public void forceRefreshPriceCache() {
        refreshPriceCache(true);
    }

    private void refreshPriceCache(boolean forceRefresh) {
        Instant now = Instant.now();
        if (!forceRefresh
                && lastCacheCheck != null
                && now.toEpochMilli() - lastCacheCheck.toEpochMilli() < feedCheckIntervalMs
                && !priceCache.isEmpty()) {
            return;
        }

        if (!isRefreshingCache.compareAndSet(false, true)) {
            return;
        }

        try {
            now = Instant.now();
            if (!forceRefresh
                    && lastCacheCheck != null
                    && now.toEpochMilli() - lastCacheCheck.toEpochMilli() < feedCheckIntervalMs
                    && !priceCache.isEmpty()) {
                return;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "InvestmentTracker/1.0");
            headers.set("Accept", "application/json");
            if (lastFeedEtag != null && !lastFeedEtag.isBlank()) {
                headers.setIfNoneMatch(lastFeedEtag);
            }
            if (lastFeedModifiedMillis > 0) {
                headers.setIfModifiedSince(lastFeedModifiedMillis);
            }

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    CSGOTRADER_STEAM_PRICES_URL,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class
            );

            lastCacheCheck = Instant.now();
            if (response.getStatusCode() == HttpStatus.NOT_MODIFIED) {
                log.debug("CSGOTrader feed has not changed");
                return;
            }

            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
                log.warn("CSGOTrader feed returned status {} without usable data", response.getStatusCode());
                return;
            }

            String json = decodeBody(body);
            Map<String, ItemPriceData> newPrices = parsePrices(json);
            if (newPrices.isEmpty()) {
                log.warn("CSGOTrader feed was valid JSON but contained no usable 24-hour prices");
                return;
            }

            BigDecimal eurRate = fetchFreshUsdToEurRate();
            long modifiedMillis = response.getHeaders().getLastModified();
            Instant observedAt = modifiedMillis > 0
                    ? Instant.ofEpochMilli(modifiedMillis)
                    : Instant.now();
            String etag = response.getHeaders().getETag();
            String version = buildSourceVersion(etag, modifiedMillis, body);

            priceCache.clear();
            priceCache.putAll(newPrices);
            cachedEurRate = eurRate;
            lastFeedEtag = etag;
            lastFeedModifiedMillis = modifiedMillis;
            lastFeedObservedAt = observedAt;
            lastFeedVersion = version;

            persistTrackedSnapshots(newPrices, eurRate, version, observedAt);
            log.info("Loaded {} CSGOTrader 24-hour prices (version {}, observed {})",
                    newPrices.size(), version, observedAt);
        } catch (Exception e) {
            lastCacheCheck = Instant.now();
            log.error("Failed to refresh the CSGOTrader price feed: {}", e.getMessage(), e);
        } finally {
            isRefreshingCache.set(false);
        }
    }

    private String decodeBody(byte[] body) throws Exception {
        boolean gzipBody = body.length >= 2
                && (body[0] & 0xff) == 0x1f
                && (body[1] & 0xff) == 0x8b;
        if (gzipBody) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(body);
                 GZIPInputStream gzip = new GZIPInputStream(input)) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private Map<String, ItemPriceData> parsePrices(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        Map<String, ItemPriceData> prices = new HashMap<>();
        root.properties().forEach(entry -> {
            JsonNode last24h = entry.getValue().get("last_24h");
            if (last24h != null && last24h.isNumber() && last24h.asDouble() > 0) {
                prices.put(entry.getKey(), new ItemPriceData(last24h.decimalValue()));
            }
        });
        return prices;
    }

    private String buildSourceVersion(String etag, long modifiedMillis, byte[] body) throws Exception {
        if (etag != null && !etag.isBlank()) {
            return etag.replace("W/", "").replace("\"", "");
        }
        if (modifiedMillis > 0) {
            return "last-modified-" + modifiedMillis;
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    private BigDecimal fetchFreshUsdToEurRate() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "https://open.er-api.com/v6/latest/USD",
                    String.class
            );
            if (response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode eur = root.path("rates").path("EUR");
                if (eur.isNumber() && eur.asDouble() > 0) {
                    return eur.decimalValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch USD/EUR rate; using fallback {}: {}", DEFAULT_EUR_RATE, e.getMessage());
        }
        return DEFAULT_EUR_RATE;
    }

    private BigDecimal getUsdToEurRate() {
        if (cachedEurRate == null) {
            cachedEurRate = fetchFreshUsdToEurRate();
        }
        return cachedEurRate;
    }

    private BigDecimal toEur(BigDecimal usdPrice, BigDecimal rate) {
        return usdPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private void persistTrackedSnapshots(
            Map<String, ItemPriceData> prices,
            BigDecimal eurRate,
            String sourceVersion,
            Instant observedAt
    ) {
        synchronized (snapshotWriteLock) {
            List<Item> trackedItems = itemRepository.findAllSteamTrackedItems();
            if (trackedItems.isEmpty()) {
                return;
            }

            List<Item> itemsToUpdate = new ArrayList<>();
            List<MarketPriceSnapshot> snapshots = new ArrayList<>();
            Set<String> processedMarketNames = new HashSet<>();

            for (Item item : trackedItems) {
                String marketHashName = item.getMarketHashName();
                ItemPriceData data = marketHashName == null ? null : prices.get(marketHashName);
                if (data == null || data.price24h == null) {
                    continue;
                }

                BigDecimal priceEur = toEur(data.price24h, eurRate);
                item.setPrice(priceEur);
                item.setSteamFoundCheck(true);
                itemsToUpdate.add(item);

                if (processedMarketNames.add(marketHashName)
                        && !marketPriceSnapshotRepository.existsByMarketHashNameAndSourceAndSourceVersion(
                        marketHashName,
                        MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                        sourceVersion
                )) {
                    snapshots.add(new MarketPriceSnapshot(
                            marketHashName,
                            MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                            sourceVersion,
                            observedAt,
                            data.price24h,
                            eurRate,
                            priceEur
                    ));
                }
            }

            if (!itemsToUpdate.isEmpty()) {
                itemRepository.saveAll(itemsToUpdate);
            }
            if (!snapshots.isEmpty()) {
                marketPriceSnapshotRepository.saveAll(snapshots);
            }
        }
    }

    /**
     * Gives a newly tracked item its first real observation from the currently
     * loaded feed. It never creates backdated points from rolling averages.
     */
    public void recordCurrentSnapshotForItem(Item item) {
        refreshPriceCacheIfNeeded();
        if (item == null || item.getMarketHashName() == null
                || lastFeedVersion == null || lastFeedObservedAt == null) {
            return;
        }

        ItemPriceData data = priceCache.get(item.getMarketHashName());
        if (data == null || data.price24h == null) {
            return;
        }

        synchronized (snapshotWriteLock) {
            if (marketPriceSnapshotRepository.existsByMarketHashNameAndSourceAndSourceVersion(
                    item.getMarketHashName(),
                    MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                    lastFeedVersion
            )) {
                return;
            }

            BigDecimal rate = getUsdToEurRate();
            marketPriceSnapshotRepository.save(new MarketPriceSnapshot(
                    item.getMarketHashName(),
                    MarketPriceSnapshot.SOURCE_CSGOTRADER_24H,
                    lastFeedVersion,
                    lastFeedObservedAt,
                    data.price24h,
                    rate,
                    toEur(data.price24h, rate)
            ));
        }
    }

    public ItemPriceData getItemPriceData(String marketHashName) {
        refreshPriceCacheIfNeeded();
        ItemPriceData data = priceCache.get(marketHashName);
        if (data == null) {
            return null;
        }
        BigDecimal eur = toEur(data.price24h, getUsdToEurRate());
        return new ItemPriceData(eur);
    }

    /**
     * Returns the latest CSGOTrader 24-hour average converted to EUR.
     * No hidden request to Steam is made when the item is missing.
     */
    public BigDecimal fetchCurrentPrice(String marketHashName) {
        ItemPriceData data = getItemPriceData(marketHashName);
        if (data == null || data.currentPrice == null) {
            throw new IllegalStateException("No CSGOTrader price is available for: " + marketHashName);
        }
        return data.currentPrice;
    }

    public void updatePricesForItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }

        refreshPriceCacheIfNeeded();
        BigDecimal rate = getUsdToEurRate();
        List<Item> modifiedItems = new ArrayList<>();

        for (Item item : itemRepository.findAllById(itemIds)) {
            ItemPriceData data = item.getMarketHashName() == null
                    ? null
                    : priceCache.get(item.getMarketHashName());
            if (data == null || data.price24h == null) {
                continue;
            }
            item.setPrice(toEur(data.price24h, rate));
            item.setSteamFoundCheck(true);
            modifiedItems.add(item);
        }

        if (!modifiedItems.isEmpty()) {
            itemRepository.saveAll(modifiedItems);
        }
        log.info("Updated {} tracked items from the current CSGOTrader feed", modifiedItems.size());
    }

    public void updateAllTrackedItems() {
        refreshPriceCacheIfNeeded();
    }

    public void forceUpdateAllTrackedItems() {
        forceRefreshPriceCache();
    }

    public void updateAllDashboardItems() {
        refreshPriceCacheIfNeeded();
        List<Long> itemIds = itemRepository.findAllSteamTrackedItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getShowInDashboard()))
                .map(Item::getId)
                .toList();
        updatePricesForItems(itemIds);
    }

    public Optional<BigDecimal> tryFetchPrice(String marketHashName) {
        try {
            return Optional.of(fetchCurrentPrice(marketHashName));
        } catch (Exception e) {
            log.warn("CSGOTrader price is unavailable for '{}': {}", marketHashName, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean validateItemOnSteam(String marketHashName) {
        return tryFetchPrice(marketHashName).isPresent();
    }
}
