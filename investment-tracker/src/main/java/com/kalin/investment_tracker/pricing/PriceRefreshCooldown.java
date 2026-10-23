package com.kalin.investment_tracker.pricing;

import lombok.RequiredArgsConstructor;
import com.kalin.investment_tracker.shared.http.RetryAfter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;

/** Shared provider backoff policy; the refresh coordinator owns its current retry deadline. */
@Component
@RequiredArgsConstructor
public class PriceRefreshCooldown {
    private final PriceFeedProperties properties;
    private final Clock clock;

    public Instant nextRetryAt(Exception failure) {
        Instant minimum = clock.instant().plusMillis(properties.getFailureRetryIntervalMs());
        if (failure instanceof RestClientResponseException response
                && (response.getStatusCode().value() == 429 || response.getStatusCode().value() == 503)
                && response.getResponseHeaders() != null) {
            return RetryAfter.retryAtOrMinimum(response.getResponseHeaders(), clock, minimum);
        }
        return minimum;
    }
}
