package com.kalin.investment_tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.config.BoundedInputStream;
import com.kalin.investment_tracker.exeption.ExternalServiceException;
import com.kalin.investment_tracker.model.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SteamPriceService {
    private static final String FEED_URL = "https://prices.csgotrader.app/latest/steam.json";
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MarketPricePersistenceService persistence;
    private final PriceFeedProperties properties;
    private final Clock clock;
    // All price/history writers in this instance use this lock. Warm readers never wait for HTTP.
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile PriceFeedSnapshot currentFeed;
    private volatile Instant lastCacheCheck;
    private volatile Instant retryAfter;

    public static class ItemPriceData {
        public final BigDecimal currentPrice;
        public final BigDecimal price24h;
        public ItemPriceData(BigDecimal price24h) { this.currentPrice = price24h; this.price24h = price24h; }
    }

    public void refreshPriceCacheIfNeeded() { refreshPriceCache(false, false); }

    /** Bypasses the normal interval/HTTP validators, but respects a provider failure cooldown. */
    public int forceRefreshPriceCache() { return refreshPriceCache(true, true); }

    private int refreshPriceCache(boolean force, boolean strict) {
        if (!refreshDue(force, strict)) return 0;
        boolean locked = false;
        try {
            locked = refreshLock.tryLock();
            if (!locked && currentFeed == null) {
                locked = refreshLock.tryLock(properties.getRefreshWaitMs(), TimeUnit.MILLISECONDS);
            }
            if (!locked) {
                if (strict) throw new ExternalServiceException("A price feed refresh is already running");
                return 0;
            }
            if (!refreshDue(force, strict)) return 0;
            try { return downloadAndPublish(force); }
            catch (ExternalServiceException failure) {
                if (strict) throw failure;
                return 0;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (strict) throw new ExternalServiceException("Price refresh was interrupted");
            return 0;
        } finally {
            if (locked) refreshLock.unlock();
        }
    }

    private boolean refreshDue(boolean force, boolean strict) {
        Instant now = clock.instant();
        if (retryAfter != null && now.isBefore(retryAfter)) {
            if (strict) throw new ExternalServiceException("Price refresh is temporarily unavailable; try again later");
            return false;
        }
        return force || currentFeed == null || lastCacheCheck == null
                || !now.isBefore(lastCacheCheck.plusMillis(properties.getCheckIntervalMs()));
    }

    private int downloadAndPublish(boolean force) {
        try {
            PriceFeedSnapshot previous = currentFeed;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "InvestmentTracker/1.0");
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            // A new publication can reuse the same ETag: prefer its Last-Modified date.
            if (!force && previous != null && previous.modifiedMillis() > 0) {
                headers.setIfModifiedSince(previous.modifiedMillis());
            } else if (!force && previous != null && previous.etag() != null) {
                headers.setIfNoneMatch(previous.etag());
            }
            ResponseEntity<byte[]> response = restTemplate.exchange(FEED_URL, HttpMethod.GET,
                    new HttpEntity<>(headers), byte[].class);
            if (previous == null && response.getStatusCode().is2xxSuccessful()
                    && response.getHeaders().getLastModified() > 0) {
                long receivedDate = response.getHeaders().getLastModified();
                boolean olderThanStored = persistence.findLatestObservationTime()
                        .filter(latest -> latest.isAfter(Instant.ofEpochMilli(receivedDate))).isPresent();
                if (olderThanStored) {
                    // Revalidate one stale CDN response on cold start; never fabricate a newer source date.
                    headers.setIfModifiedSince(receivedDate);
                    response = restTemplate.exchange(FEED_URL, HttpMethod.GET,
                            new HttpEntity<>(headers), byte[].class);
                }
            }
            if (response.getStatusCode() == HttpStatus.NOT_MODIFIED && previous != null) {
                lastCacheCheck = clock.instant();
                retryAfter = null;
                return 0;
            }
            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.length == 0) {
                throw new ExternalServiceException("The price source returned no usable data");
            }
            byte[] decoded = decodeBody(body);
            Map<String, BigDecimal> prices = parsePrices(decoded);
            long modified = response.getHeaders().getLastModified();
            if (modified > clock.instant().plusSeconds(300).toEpochMilli()
                    || (previous != null && modified > 0 && previous.modifiedMillis() > modified)
                    || (modified > 0 && persistence.findLatestObservationTime()
                            .filter(latest -> latest.isAfter(Instant.ofEpochMilli(modified))).isPresent())) {
                throw new ExternalServiceException("The price source returned an invalid publication date");
            }
            String etag = response.getHeaders().getETag();
            String version = buildSourceVersion(etag, modified, decoded);
            boolean samePublication = previous != null && version.equals(previous.sourceVersion());
            if (samePublication && !prices.equals(previous.pricesUsd())) {
                throw new ExternalServiceException("The price publication changed without a new source version");
            }
            Optional<MarketPricePersistenceService.RecordedPublication> recorded = persistence.findRecordedPublication(version);
            BigDecimal rate = samePublication ? previous.usdToEurRate()
                    : recorded.map(MarketPricePersistenceService.RecordedPublication::rate).orElseGet(this::fetchUsdToEurRate);
            Instant observedAt = modified > 0 ? Instant.ofEpochMilli(modified)
                    : samePublication ? previous.observedAt()
                    : recorded.map(MarketPricePersistenceService.RecordedPublication::observedAt).orElseGet(clock::instant);
            PriceFeedSnapshot next = new PriceFeedSnapshot(prices, rate, version, observedAt, etag, modified);
            // Publish only after the transaction commits, retaining the previous cache on failure.
            int count = persistence.persistFeed(next);
            currentFeed = next;
            lastCacheCheck = clock.instant();
            retryAfter = null;
            log.info("Loaded {} CSGOTrader prices; stored {} daily observations (source date {})", prices.size(), count, observedAt);
            return count;
        } catch (Exception failure) {
            retryAfter = nextRetryAt(failure);
            log.warn("Price refresh failed ({}); retaining previous data, next attempt after {}",
                    failure.getClass().getSimpleName(), retryAfter);
            throw new ExternalServiceException("Price feed refresh failed; previous data was retained");
        }
    }

    private byte[] decodeBody(byte[] body) throws Exception {
        boolean gzip = body.length >= 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
        InputStream input = new ByteArrayInputStream(body);
        if (gzip) input = new GZIPInputStream(input);
        try (InputStream bounded = new BoundedInputStream(input, properties.getMaximumDecodedBytes())) {
            return bounded.readAllBytes();
        }
    }

    private Map<String, BigDecimal> parsePrices(byte[] json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject()) throw new ExternalServiceException("Invalid price feed format");
        Map<String, BigDecimal> prices = new HashMap<>();
        for (var entry : root.properties()) {
            JsonNode value = entry.getValue().path("last_24h");
            if (entry.getKey().isBlank() || entry.getKey().length() > 500 || !value.isNumber()) continue;
            BigDecimal usd = value.decimalValue().setScale(4, RoundingMode.HALF_UP);
            if (usd.signum() > 0 && usd.precision() <= 18) prices.put(entry.getKey(), usd);
        }
        if (prices.isEmpty()) throw new ExternalServiceException("No usable 24-hour prices were returned");
        return prices;
    }

    private String buildSourceVersion(String etag, long modified, byte[] decoded) throws Exception {
        String identity = etag == null || etag.isBlank() ? hash(decoded) : etag;
        // Hash untrusted ETags so the stored identifier always fits the existing column.
        String identityHash = hash(identity.getBytes(StandardCharsets.UTF_8));
        if (modified > 0) return "published-" + modified + "-" + identityHash;
        if (etag != null && !etag.isBlank()) {
            String legacyVersion = etag.replace("W/", "").replace("\"", "");
            if (!legacyVersion.isBlank() && legacyVersion.length() <= 128) return legacyVersion;
            return "content-" + identityHash;
        }
        return hash(decoded);
    }

    private String hash(byte[] body) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    }

    private BigDecimal fetchUsdToEurRate() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(FX_URL, String.class);
            JsonNode eur = objectMapper.readTree(response.getBody()).path("rates").path("EUR");
            if (response.getStatusCode().is2xxSuccessful() && eur.isNumber()) {
                BigDecimal rate = eur.decimalValue().setScale(8, RoundingMode.HALF_UP);
                if (rate.signum() > 0 && rate.precision() <= 18) return rate;
            }
        } catch (Exception failure) {
            log.warn("USD/EUR lookup failed ({}); checking the last received or stored rate", failure.getClass().getSimpleName());
        }
        PriceFeedSnapshot previous = currentFeed;
        return previous != null ? previous.usdToEurRate() : persistence.findLastKnownRate()
                .orElseThrow(() -> new ExternalServiceException("No received or stored USD/EUR conversion rate is available"));
    }

    private Instant nextRetryAt(Exception failure) {
        Instant minimum = clock.instant().plusMillis(properties.getFailureRetryIntervalMs());
        if (failure instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 429 || response.getStatusCode().value() == 503)
                && response.getResponseHeaders() != null) {
            String retry = response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            if (retry != null) {
                try {
                    Instant requested = retry.matches("[0-9]+") ? clock.instant().plusSeconds(Long.parseLong(retry))
                            : ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    if (requested.isAfter(minimum)) return requested;
                } catch (RuntimeException ignored) { /* Invalid provider header: use the configured cooldown. */ }
            }
        }
        return minimum;
    }

    public void recordCurrentSnapshotForItem(Item item) {
        if (item == null || item.getId() == null || item.getMarketHashName() == null) return;
        refreshPriceCacheIfNeeded();
        refreshLock.lock();
        try {
            if (currentFeed != null) persistence.applySelected(currentFeed, List.of(item.getId()));
        } finally { refreshLock.unlock(); }
    }

    public ItemPriceData getItemPriceData(String marketHashName) {
        refreshPriceCacheIfNeeded();
        PriceFeedSnapshot feed = currentFeed;
        BigDecimal eur = feed == null || marketHashName == null ? null : feed.priceEur(marketHashName);
        return eur == null ? null : new ItemPriceData(eur);
    }

    /** Value a whole inventory against one immutable publication and one conversion rate. */
    public Map<String, BigDecimal> getCurrentPrices(Collection<String> marketNames) {
        if (marketNames == null || marketNames.isEmpty()) return Map.of();
        refreshPriceCacheIfNeeded();
        PriceFeedSnapshot feed = currentFeed;
        if (feed == null) throw new ExternalServiceException("Inventory prices are temporarily unavailable; try again later");
        Map<String, BigDecimal> prices = new HashMap<>();
        for (String name : marketNames) {
            if (name == null) continue;
            BigDecimal price = feed.priceEur(name);
            if (price != null) prices.put(name, price);
        }
        return Map.copyOf(prices);
    }

    /** CSGOTrader 24-hour average in EUR; no hidden request to Steam for a missing item. */
    public BigDecimal fetchCurrentPrice(String marketHashName) {
        ItemPriceData data = getItemPriceData(marketHashName);
        if (data == null) throw new ExternalServiceException("No CSGOTrader price is available for this item");
        return data.currentPrice;
    }

    public void updatePricesForItems(List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        refreshPriceCache(false, true);
        refreshLock.lock();
        try { if (currentFeed != null) persistence.applySelected(currentFeed, itemIds); }
        finally { refreshLock.unlock(); }
    }

    public int updateAllTrackedItems() { return refreshPriceCache(false, true); }
    public int forceUpdateAllTrackedItems() { return forceRefreshPriceCache(); }
    public void updateAllDashboardItems() {
        refreshPriceCache(false, true);
        refreshLock.lock();
        try { if (currentFeed != null) persistence.applyDashboard(currentFeed); }
        finally { refreshLock.unlock(); }
    }
    public Optional<BigDecimal> tryFetchPrice(String marketHashName) {
        try { return Optional.of(fetchCurrentPrice(marketHashName)); }
        catch (ExternalServiceException unavailable) { return Optional.empty(); }
    }
    public boolean validateItemOnSteam(String marketHashName) { return tryFetchPrice(marketHashName).isPresent(); }
}
