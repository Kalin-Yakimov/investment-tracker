package com.kalin.investment_tracker.pricing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "price.feed")
public class PriceFeedProperties {
    @Min(1) private long checkIntervalMs = 7200000;
    @Min(1) private long failureRetryIntervalMs = 60000;
    @Min(1) private long maximumPublicationAgeMs = 129600000;
    @Min(1) private long maximumFxAgeMs = 259200000;
    @Min(1) @Max(60000) private long refreshWaitMs = 25000;
    @Min(1024) @Max(134217728) private int maximumDecodedBytes = 67108864;
}
