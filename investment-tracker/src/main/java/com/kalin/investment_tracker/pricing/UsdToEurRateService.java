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

/** Received rate first, previous/stored rate as fallback; never an invented fixed rate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsdToEurRateService {
    private static final String FX_URL = "https://open.er-api.com/v6/latest/USD";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MarketPricePersistenceService persistence;

    public BigDecimal getRate(PriceFeedSnapshot previous) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(FX_URL, String.class);
            JsonNode eur = objectMapper.readTree(response.getBody()).path("rates").path("EUR");
            if (response.getStatusCode().is2xxSuccessful() && eur.isNumber()) {
                BigDecimal rate = eur.decimalValue().setScale(8, RoundingMode.HALF_UP);
                if (rate.signum() > 0 && rate.precision() <= 18) return rate;
            }
        } catch (Exception failure) {
            log.warn("USD/EUR lookup failed ({}); checking the last received or stored rate", failure.getClass().getSimpleName());
        }
        return previous != null ? previous.usdToEurRate() : persistence.findLastKnownRate()
                .orElseThrow(() -> new ExternalServiceException("No received or stored USD/EUR conversion rate is available"));
    }
}
