package com.kalin.investment_tracker.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "external.http")
public class ExternalHttpProperties {
    @NotNull private Duration connectTimeout = Duration.ofSeconds(5);
    @NotNull private Duration readTimeout = Duration.ofSeconds(20);
    @Min(1024) @Max(134217728) private int maximumResponseBytes = 33554432;

    @AssertTrue(message = "External HTTP timeouts must be between 1ms and 120s")
    public boolean isTimeoutRangeValid() {
        return valid(connectTimeout) && valid(readTimeout);
    }

    private boolean valid(Duration duration) {
        return duration != null && duration.compareTo(Duration.ofMillis(1)) >= 0
                && duration.compareTo(Duration.ofSeconds(120)) <= 0;
    }
}
