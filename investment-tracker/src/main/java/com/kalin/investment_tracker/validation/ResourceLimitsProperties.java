package com.kalin.investment_tracker.validation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Single-instance admission budgets, not provider promises or product subscription tiers. */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.limits")
public class ResourceLimitsProperties {
    @Min(1024) @Max(1048576) private int requestBodyBytes = 65536;
    @Min(1) private int requestsPerIpMinute = 3000;
    @Min(1) private int requestsPerUserMinute = 600;
    @Min(1) private int writesPerUserMinute = 60;
    @Min(1) private int inventoriesPerUserMinute = 10;
    @Min(1) private int priceUpdatesPerUserMinute = 2;
    @Min(1) private int contactRequestsPerUserMinute = 5;
    @Min(1) private int maintenancePerUserMinute = 4;
    @Min(100) @Max(1000000) private int maximumRateLimitEntries = 20000;
    @Min(1) @Max(100000) private int trackedItemsPerUser = 1000;
}
