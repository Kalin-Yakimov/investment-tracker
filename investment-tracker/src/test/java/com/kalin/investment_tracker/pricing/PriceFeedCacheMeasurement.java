package com.kalin.investment_tracker.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PriceFeedCacheMeasurement {

    private static final String MARKET_HASH_NAME = "AK-47 | Redline (Field-Tested)";
    private static final int WARM_RUNS = 30;

    @Test
    @Disabled("Manual network measurement; excluded from the deterministic test suite")
    void measureColdAndWarmPriceLookup() throws Exception {
        var http = new com.kalin.investment_tracker.config.ExternalHttpConfig(
                new com.kalin.investment_tracker.config.ExternalHttpProperties()).restTemplate();
        var settings = new PriceFeedProperties();
        var persistence = mock(MarketPricePersistenceService.class);
        var clock = java.time.Clock.systemUTC();
        PriceFeedService service = new PriceFeedService(
                new CsgotraderFeedClient(http, new ObjectMapper(), settings),
                new UsdToEurRateService(http, new ObjectMapper(), persistence),
                new PriceRefreshCooldown(settings, clock), persistence, settings, clock);


        long coldStarted = System.nanoTime();
        BigDecimal coldPrice = service.fetchCurrentPrice(MARKET_HASH_NAME);
        double coldMilliseconds = elapsedMilliseconds(coldStarted);

        assertNotNull(coldPrice);
        assertTrue(coldPrice.compareTo(BigDecimal.ZERO) > 0);

        // One unmeasured warm-up call stabilizes class loading and JIT effects.
        BigDecimal warmUpPrice = service.fetchCurrentPrice(MARKET_HASH_NAME);
        assertEquals(coldPrice, warmUpPrice);

        double[] warmMeasurements = new double[WARM_RUNS];
        for (int i = 0; i < WARM_RUNS; i++) {
            long warmStarted = System.nanoTime();
            BigDecimal warmPrice = service.fetchCurrentPrice(MARKET_HASH_NAME);
            warmMeasurements[i] = elapsedMilliseconds(warmStarted);
            assertEquals(coldPrice, warmPrice);
        }

        Arrays.sort(warmMeasurements);
        double warmMedianMilliseconds = median(warmMeasurements);
        double warmAverageMilliseconds = Arrays.stream(warmMeasurements).average().orElseThrow();

        System.out.printf(
                "CACHE_MEASUREMENT marketHashName=%s coldMs=%.3f warmMedianMs=%.3f warmAverageMs=%.3f runs=%d priceEur=%s%n",
                MARKET_HASH_NAME,
                coldMilliseconds,
                warmMedianMilliseconds,
                warmAverageMilliseconds,
                WARM_RUNS,
                coldPrice
        );
    }

    private static double elapsedMilliseconds(long startedAtNanoseconds) {
        return (System.nanoTime() - startedAtNanoseconds) / 1_000_000.0;
    }

    private static double median(double[] sortedValues) {
        int middle = sortedValues.length / 2;
        if (sortedValues.length % 2 == 0) {
            return (sortedValues[middle - 1] + sortedValues[middle]) / 2.0;
        }
        return sortedValues[middle];
    }
}
