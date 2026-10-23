package com.kalin.investment_tracker.pricing;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsdToEurRateServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    @Mock RestTemplate http;
    @Mock MarketPricePersistenceService persistence;
    private UsdToEurRateService service;
    @BeforeEach void setup() { service = new UsdToEurRateService(http, new ObjectMapper(), persistence, Clock.fixed(NOW, ZoneOffset.UTC)); }
    @Test void readsTheProviderAsOfTimestampRatherThanTheLocalRequestTime() {
        Instant asOf = NOW.minusSeconds(86400);
        response("{\"result\":\"success\",\"base_code\":\"USD\",\"time_last_update_unix\":" + asOf.getEpochSecond() + ",\"rates\":{\"EUR\":0.92}}");
        var quote = service.getQuote(null);
        assertEquals(asOf, quote.asOf()); assertEquals(new BigDecimal("0.92000000"), quote.rate());
        verifyNoInteractions(persistence);
    }
    @Test void anUndatedButUsableResponseStaysExplicitlyUndated() {
        response("{\"rates\":{\"EUR\":0.92}}");
        assertNull(service.getQuote(null).asOf());
    }
    @Test void fallbackPreservesTheOriginalRateDateAcrossRestart() {
        when(http.getForEntity(anyString(), eq(String.class))).thenThrow(new IllegalStateException("offline"));
        Instant oldDate = NOW.minusSeconds(604800);
        var stored = new UsdToEurRateService.Quote(new BigDecimal("0.92"), oldDate);
        when(persistence.findLastKnownQuote()).thenReturn(Optional.of(stored));
        assertEquals(stored, service.getQuote(null));
        var previous = new PriceFeedSnapshot(Map.of(), stored.rate(), "previous", NOW.minusSeconds(100), null, -1, oldDate);
        assertEquals(stored, service.getQuote(previous));
        verify(persistence).findLastKnownQuote();
    }
    @Test void futureTimestampOrWrongBaseCannotBecomeANewQuote() {
        var previous = new PriceFeedSnapshot(Map.of(), new BigDecimal("0.92"), "previous", NOW.minusSeconds(100), null, -1, NOW.minusSeconds(86400));
        response("{\"base_code\":\"EUR\",\"rates\":{\"EUR\":1}}");
        assertEquals(previous.usdToEurRate(), service.getQuote(previous).rate());
        response("{\"time_last_update_unix\":" + NOW.plusSeconds(86400).getEpochSecond() + ",\"rates\":{\"EUR\":1}}");
        assertEquals(previous.fxRateAsOf(), service.getQuote(previous).asOf());
    }
    @Test void unavailableRateWithoutAnyFallbackNeverInventsAFixedConversion() {
        response("{\"rates\":{\"EUR\":0}}");
        when(persistence.findLastKnownQuote()).thenReturn(Optional.empty());
        assertThrows(com.kalin.investment_tracker.exception.ExternalServiceException.class, () -> service.getQuote(null));
    }
    private void response(String body) { when(http.getForEntity(anyString(), eq(String.class))).thenReturn(ResponseEntity.ok(body)); }
}
