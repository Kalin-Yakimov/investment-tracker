package com.kalin.investment_tracker.shared.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryAfterTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant MINIMUM = NOW.plusSeconds(60);

    @ParameterizedTest
    @CsvSource({"0,60", "30,60", "120,120", "3600,3600"})
    void numericHintsCanExtendButNeverShortenMinimum(String value, long expectedSeconds) {
        assertEquals(NOW.plusSeconds(expectedSeconds), resolve(value));
    }

    @ParameterizedTest
    @MethodSource("datedHints")
    void httpDateHintsRetainTheSameMinimumRule(String value, Instant expected) {
        assertEquals(expected, resolve(value));
    }

    private static Stream<Arguments> datedHints() {
        return Stream.of(-60L, 30L, 120L).map(seconds -> Arguments.of(
                DateTimeFormatter.RFC_1123_DATE_TIME.format(NOW.plusSeconds(seconds).atZone(ZoneOffset.UTC)),
                seconds > 60 ? NOW.plusSeconds(seconds) : MINIMUM));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "1.5", "not-a-date", "999999999999999999999999999999",
            "2026-10-12T10:00:00Z"})
    void malformedHintsKeepTheMinimum(String value) {
        assertEquals(MINIMUM, resolve(value));
    }

    @Test
    void missingHeadersKeepTheMinimum() {
        assertEquals(MINIMUM, RetryAfter.retryAtOrMinimum(null, CLOCK, MINIMUM));
        assertEquals(MINIMUM, RetryAfter.retryAtOrMinimum(new HttpHeaders(), CLOCK, MINIMUM));
    }

    private Instant resolve(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, value);
        return RetryAfter.retryAtOrMinimum(headers, CLOCK, MINIMUM);
    }
}
