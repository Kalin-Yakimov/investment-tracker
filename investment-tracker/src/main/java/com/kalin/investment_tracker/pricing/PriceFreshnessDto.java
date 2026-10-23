package com.kalin.investment_tracker.pricing;

import java.time.Instant;

/** Source timestamps are nullable: a local check is never invented as a provider publication. */
public record PriceFreshnessDto(String status, String source, Instant observedAt, Instant sourcePublishedAt,
                                Instant lastSuccessfulCheck, Instant fxRateAsOf, boolean storedFallback,
                                boolean sourceStale, boolean fxStale) {
    public static PriceFreshnessDto unknownStored(boolean hasPrice) {
        return new PriceFreshnessDto(hasPrice ? "STORED_FALLBACK" : "UNAVAILABLE",
                MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, null, null, null, null, hasPrice, false, false);
    }

    public static PriceFreshnessDto describe(Instant observedAt, Instant publishedAt, Instant checkedAt,
                                             Instant fxAsOf, boolean storedFallback, Instant now, PriceFeedProperties policy) {
        boolean oldSource = publishedAt != null && publishedAt.plusMillis(policy.getMaximumPublicationAgeMs()).isBefore(now);
        boolean oldFx = fxAsOf != null && fxAsOf.plusMillis(policy.getMaximumFxAgeMs()).isBefore(now);
        String status = storedFallback ? "STORED_FALLBACK" : oldSource || oldFx ? "STALE"
                : publishedAt == null || fxAsOf == null || checkedAt == null ? "UNKNOWN" : "FRESH";
        return new PriceFreshnessDto(status, MarketPriceSnapshot.SOURCE_CSGOTRADER_24H, observedAt,
                publishedAt, checkedAt, fxAsOf, storedFallback, oldSource, oldFx);
    }
}
