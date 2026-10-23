package com.kalin.investment_tracker.inventory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "inventory.steam")
public class SteamInventoryProperties {
    @Min(1) @Max(25) private int maximumPages = 5;
    @Min(1) @Max(50000) private int maximumAssets = 10000;
    @Min(1) @Max(1000000) private int maximumQuantity = 100000;
    @Min(0) @Max(5000) private long pageDelayMs = 1000;
    @Min(1000) @Max(3600000) private long failureRetryIntervalMs = 60000;
    @Min(1) @Max(20) private int maximumConcurrentCalculations = 2;
    @Min(1) @Max(100) private int maximumConcurrentLookups = 16;
    @Min(1) @Max(20) private int maximumConcurrentRequests = 2;
    @Min(1) @Max(10000) private long minimumRequestIntervalMs = 1000;
    @Min(1) @Max(10000) private long maximumRequestWaitMs = 2000;
}
