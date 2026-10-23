package com.kalin.investment_tracker.shared.http;

import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Parses provider hints; callers retain their own status checks and cooldown policies. */
public final class RetryAfter {
    private RetryAfter() {
    }

    public static Instant retryAtOrMinimum(HttpHeaders headers, Clock clock, Instant minimum) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                Instant requested = value.matches("[0-9]+")
                        ? clock.instant().plusSeconds(Long.parseLong(value))
                        : ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                if (requested.isAfter(minimum)) return requested;
            } catch (RuntimeException ignored) {
                // Missing/invalid hints never shorten the caller's minimum delay.
            }
        }
        return minimum;
    }
}
