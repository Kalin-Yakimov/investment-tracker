package com.kalin.investment_tracker.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
}
