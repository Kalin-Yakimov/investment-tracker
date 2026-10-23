package com.kalin.investment_tracker.pricing;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class PriceFreshnessTest {
    private final Instant now = Instant.parse("2026-10-21T12:00:00Z");
    private final PriceFeedProperties policy = new PriceFeedProperties();
    @Test void recentConfirmedSourceAndDatedFxAreFresh() {
        var metadata = PriceFreshnessDto.describe(now.minusSeconds(3600), now.minusSeconds(3600), now,
                now.minusSeconds(86400), false, now, policy);
        assertEquals("FRESH", metadata.status()); assertFalse(metadata.sourceStale()); assertFalse(metadata.fxStale());
    }
    @Test void successfulRecheckDoesNotMakeAnOldPublicationFresh() {
        var metadata = PriceFreshnessDto.describe(now.minusSeconds(172800), now.minusSeconds(172800), now,
                now, false, now, policy);
        assertEquals("STALE", metadata.status()); assertTrue(metadata.sourceStale()); assertEquals(now, metadata.lastSuccessfulCheck());
    }
    @Test void oldFxRemainsOldEvenWhenThePriceSourceIsNew() {
        var metadata = PriceFreshnessDto.describe(now, now, now, now.minusSeconds(345600), false, now, policy);
        assertEquals("STALE", metadata.status()); assertTrue(metadata.fxStale()); assertFalse(metadata.sourceStale());
    }
    @Test void unknownDatesAreNotFabricatedFromLocalCheckTime() {
        var metadata = PriceFreshnessDto.describe(now, null, now, null, false, now, policy);
        assertEquals("UNKNOWN", metadata.status()); assertNull(metadata.sourcePublishedAt()); assertNull(metadata.fxRateAsOf());
        assertEquals("STORED_FALLBACK", PriceFreshnessDto.unknownStored(true).status());
        assertEquals("UNAVAILABLE", PriceFreshnessDto.unknownStored(false).status());
    }
}
