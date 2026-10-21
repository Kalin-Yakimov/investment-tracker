package com.kalin.investment_tracker.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Data
@Validated
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {
    @Min(0) @Max(100000) private int initialCapacity = 100;
    @Min(1) @Max(1000000) private long maximumSize = 1000;
    @Valid @NotNull private Policy csItemsSearch = new Policy(Duration.ofHours(12));
    @Valid @NotNull private Policy portfolioSummary = new Policy(Duration.ofMinutes(15));
    @Valid @NotNull private Policy inventoryCalculator = new Policy(Duration.ofMinutes(15));
    @Valid @NotNull private Policy steamProfileResolution = new Policy(Duration.ofMinutes(15));
    // Each inventory weighs at least 100 units; each returned item adds to its bounded footprint.
    @Min(100) @Max(10000000) private long inventoryMaximumWeight = 20000;

    @Data
    public static class Policy {
        @NotNull private Duration ttl;
        @Min(1) @Max(1000000) private Long maximumSize;
        public Policy() { this(Duration.ofMinutes(15)); }
        public Policy(Duration ttl) { this.ttl = ttl; }
        @AssertTrue(message = "Cache TTL must be positive and no more than 7 days")
        public boolean isTtlValid() { return ttl != null && !ttl.isNegative() && !ttl.isZero() && ttl.compareTo(Duration.ofDays(7)) <= 0; }
    }
    public long maximum(Policy policy) { return policy.getMaximumSize() == null ? maximumSize : policy.getMaximumSize(); }
}
