package com.kalin.investment_tracker.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** One immutable publication: values, conversion rate and source identity move together. */
public record PriceFeedSnapshot(
        Map<String, BigDecimal> pricesUsd,
        BigDecimal usdToEurRate,
        String sourceVersion,
        Instant observedAt,
        String etag,
        long modifiedMillis
) {
    public PriceFeedSnapshot {
        pricesUsd = Map.copyOf(pricesUsd);
        Objects.requireNonNull(usdToEurRate);
        Objects.requireNonNull(sourceVersion);
        Objects.requireNonNull(observedAt);
    }

    public BigDecimal priceEur(String marketName) {
        BigDecimal usd = pricesUsd.get(marketName);
        return usd == null ? null : usd.multiply(usdToEurRate).setScale(2, RoundingMode.HALF_UP);
    }
}
