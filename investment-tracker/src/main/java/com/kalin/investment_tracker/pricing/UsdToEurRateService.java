package com.kalin.investment_tracker.pricing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.kalin.investment_tracker.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

/** Received rate first, previous/stored rate as fallback; never an invented fixed rate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsdToEurRateService {
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MarketPricePersistenceService persistence;
    private final Clock clock;

    public record Quote(BigDecimal rate, Instant asOf) { }

    public Quote getQuote(PriceFeedSnapshot previous) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(FX_URL, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode eur = root.path("rates").path("EUR");
            if (root.has("result") && !"success".equals(root.path("result").asString())
                    || root.has("base_code") && !"USD".equals(root.path("base_code").asString())) {
                throw new ExternalServiceException("Unexpected currency response");
            }
            if (response.getStatusCode().is2xxSuccessful() && eur.isNumber()) {
                BigDecimal rate = eur.decimalValue().setScale(8, RoundingMode.HALF_UP);
                if (rate.signum() > 0 && rate.precision() <= 18) {
                    JsonNode timestamp = root.path("time_last_update_unix");
                    Instant asOf = timestamp.isIntegralNumber() && timestamp.canConvertToLong() && timestamp.longValue() > 0
                            ? Instant.ofEpochSecond(timestamp.longValue()) : null;
                    if (asOf != null && asOf.isAfter(clock.instant().plusSeconds(300))) {
                        throw new ExternalServiceException("Invalid currency publication timestamp");
                    }
                    return new Quote(rate, asOf);
                }
            }
        } catch (Exception failure) {
            log.warn("USD/EUR lookup failed ({}); checking the last received or stored rate", failure.getClass().getSimpleName());
        }
        return previous != null ? new Quote(previous.usdToEurRate(), previous.fxRateAsOf()) : persistence.findLastKnownQuote()
                .orElseThrow(() -> new ExternalServiceException("No received or stored USD/EUR conversion rate is available"));
    }
}
