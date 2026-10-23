package com.kalin.investment_tracker.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "security.verification")
public class AuthProtectionProperties {
    private Duration codeLifetime = Duration.ofMinutes(15);
    private Duration requestWindow = Duration.ofHours(1);
    private Duration attemptWindow = Duration.ofMinutes(15);
    private Duration resendCooldown = Duration.ofSeconds(60);
    @Min(1) private int requestsPerEmail = 3;
    @Min(1) private int requestsPerIp = 20;
    @Min(1) private int registrationsPerIp = 5;
    @Min(1) private int attemptsPerEmail = 5;
    @Min(1) private int attemptsPerIp = 30;
    @Min(1) private int loginsPerEmail = 10;
    @Min(1) private int loginsPerIp = 30;
    @Min(1) private int maximumLimitEntries = 10_000;
    @Min(1) private int expiredCodeRetentionDays = 7;

    @AssertTrue(message = "Verification durations must be positive; attempt window must cover the code lifetime")
    public boolean isDurationConfigurationValid() {
        return positive(codeLifetime) && positive(requestWindow) && positive(attemptWindow)
                && positive(resendCooldown) && attemptWindow.compareTo(codeLifetime) >= 0
                && requestWindow.compareTo(resendCooldown) >= 0;
    }

    private boolean positive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
