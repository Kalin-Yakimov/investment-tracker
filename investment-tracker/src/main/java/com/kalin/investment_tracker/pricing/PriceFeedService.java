package com.kalin.investment_tracker.pricing;

import com.kalin.investment_tracker.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceFeedService {
    private final CsgotraderFeedClient feedClient;
    private final UsdToEurRateService currencyRates;
    private final PriceRefreshCooldown cooldown;
    private final MarketPricePersistenceService persistence;
    private final PriceFeedProperties properties;
    private final Clock clock;
    // All price/history writers in this instance use this lock. Warm readers never wait for HTTP.
    private final ReentrantLock refreshLock = new ReentrantLock();
    private record CachedFeed(PriceFeedSnapshot publication, Instant checkedAt) { }
    private volatile CachedFeed cache = new CachedFeed(null, null);
    private volatile Instant retryAfter;

    public static class ItemPriceData {
        public final BigDecimal currentPrice;
        public final BigDecimal price24h;
        public final PriceFreshnessDto freshness;
        public ItemPriceData(BigDecimal price24h) { this(price24h, null); }
        public ItemPriceData(BigDecimal price24h, PriceFreshnessDto freshness) {
            this.currentPrice = price24h; this.price24h = price24h; this.freshness = freshness;
        }
    }

    public void refreshPriceCacheIfNeeded() { refreshPriceCache(false, false); }

    /** Bypasses the normal interval/HTTP validators, but respects a provider failure cooldown. */
    public int forceRefreshPriceCache() { return refreshPriceCache(true, true); }

    private int refreshPriceCache(boolean force, boolean strict) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Price downloads must run outside a database transaction");
        }
        if (!refreshDue(force, strict)) return 0;
        boolean locked = false;
        try {
            locked = refreshLock.tryLock();
            if (!locked && cache.publication() == null) {
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
        CachedFeed current = cache;
        return force || current.publication() == null || current.checkedAt() == null
                || !now.isBefore(current.checkedAt().plusMillis(properties.getCheckIntervalMs()));
    }

    private int downloadAndPublish(boolean force) {
        try {
            PriceFeedSnapshot previous = cache.publication();
            ResponseEntity<byte[]> response = feedClient.download(previous, force);
            if (previous == null && response.getStatusCode().is2xxSuccessful()
                    && response.getHeaders().getLastModified() > 0) {
                long receivedDate = response.getHeaders().getLastModified();
                boolean olderThanStored = persistence.findLatestObservationTime()
                        .filter(latest -> latest.isAfter(Instant.ofEpochMilli(receivedDate))).isPresent();
                if (olderThanStored) {
                    // One cold-start revalidation. Never fabricate a newer publication date.
                    response = feedClient.revalidate(receivedDate);
                }
            }

            if (response.getStatusCode() == HttpStatus.NOT_MODIFIED && previous != null) {
                cache = new CachedFeed(previous, clock.instant());
                retryAfter = null;
                return 0;
            }
            CsgotraderFeedClient.Publication publication = feedClient.readPublication(response);
            Map<String, BigDecimal> prices = publication.prices();
            long modified = publication.modified();

            if (modified > clock.instant().plusSeconds(300).toEpochMilli()
                    || (previous != null && modified > 0 && previous.modifiedMillis() > modified)
                    || (modified > 0 && persistence.findLatestObservationTime()
                            .filter(latest -> latest.isAfter(Instant.ofEpochMilli(modified))).isPresent())) {
                throw new ExternalServiceException("The price source returned an invalid publication date");
            }
            String etag = publication.etag();
            String version = publication.version();
            boolean samePublication = previous != null && version.equals(previous.sourceVersion());
            if (samePublication && !prices.equals(previous.pricesUsd())) {
                throw new ExternalServiceException("The price publication changed without a new source version");
            }
            Optional<MarketPricePersistenceService.RecordedPublication> recorded = persistence.findRecordedPublication(version);
            UsdToEurRateService.Quote quote = samePublication ? new UsdToEurRateService.Quote(previous.usdToEurRate(), previous.fxRateAsOf())
                    : recorded.map(value -> new UsdToEurRateService.Quote(value.rate(), value.fxRateAsOf())).orElseGet(() -> currencyRates.getQuote(previous));
            Instant observedAt = modified > 0 ? Instant.ofEpochMilli(modified)
                    : samePublication ? previous.observedAt()
                    : recorded.map(MarketPricePersistenceService.RecordedPublication::observedAt).orElseGet(clock::instant);
            PriceFeedSnapshot next = new PriceFeedSnapshot(prices, quote.rate(), version, observedAt, etag, modified, quote.asOf());
            // Publish only after the transaction commits, retaining the previous cache on failure.
            int count = persistence.persistFeed(next);
            cache = new CachedFeed(next, clock.instant());
            retryAfter = null;
            log.info("Loaded {} CSGOTrader prices; stored {} daily observations (source date {})", prices.size(), count, observedAt);
            return count;
        } catch (Exception failure) {
            retryAfter = cooldown.nextRetryAt(failure);
            log.warn("Price refresh failed ({}); retaining previous data, next attempt after {}",
                    failure.getClass().getSimpleName(), retryAfter);
            throw new ExternalServiceException("Price feed refresh failed; previous data was retained");
        }
    }

    /** Immutable, already committed publication. Never performs HTTP or database work. */
    public PriceFeedSnapshot getCachedPublication() { return cache.publication(); }

    public ItemPriceData getItemPriceData(String marketHashName) {
        refreshPriceCacheIfNeeded();
        return getCachedItemPriceData(marketHashName);
    }

    /** Pure cache read used by response mapping after the caller's one refresh check. */
    public ItemPriceData getCachedItemPriceData(String marketHashName) {
        CachedFeed current = cache;
        PriceFeedSnapshot feed = current.publication();
        BigDecimal eur = feed == null || marketHashName == null ? null : feed.priceEur(marketHashName);
        return eur == null ? null : new ItemPriceData(eur, PriceFreshnessDto.describe(feed.observedAt(),
                feed.modifiedMillis() > 0 ? Instant.ofEpochMilli(feed.modifiedMillis()) : null,
                current.checkedAt(), feed.fxRateAsOf(), false, clock.instant(), properties));
    }

    /** Pure metadata construction, including inside an item mutation; never downloads prices. */
    public ItemPriceData describeStoredPrice(MarketPricePersistenceService.RecordedMarketPrice stored) {
        return new ItemPriceData(stored.price(), PriceFreshnessDto.describe(stored.observedAt(), stored.sourcePublishedAt(),
                null, stored.fxRateAsOf(), true, clock.instant(), properties));
    }

    /** Value a whole inventory against one immutable publication and one conversion rate. */
    public Map<String, BigDecimal> getCurrentPrices(Collection<String> marketNames) {
        if (marketNames == null || marketNames.isEmpty()) return Map.of();
        refreshPriceCacheIfNeeded();
        PriceFeedSnapshot feed = cache.publication();
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
        try { if (cache.publication() != null) persistence.applySelected(cache.publication(), itemIds); }
        finally { refreshLock.unlock(); }
    }

    public int updateAllTrackedItems() { return refreshPriceCache(false, true); }
    public int forceUpdateAllTrackedItems() { return forceRefreshPriceCache(); }
    public void updateAllDashboardItems() {
        refreshPriceCache(false, true);
        refreshLock.lock();
        try { if (cache.publication() != null) persistence.applyDashboard(cache.publication()); }
        finally { refreshLock.unlock(); }
    }
    public Optional<BigDecimal> tryFetchPrice(String marketHashName) {
        try { return Optional.of(fetchCurrentPrice(marketHashName)); }
        catch (ExternalServiceException unavailable) { return Optional.empty(); }
    }
    public boolean validateItemOnSteam(String marketHashName) { return tryFetchPrice(marketHashName).isPresent(); }
}
