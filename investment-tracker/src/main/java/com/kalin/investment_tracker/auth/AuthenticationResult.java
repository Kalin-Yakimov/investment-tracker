package com.kalin.investment_tracker.auth;

import java.time.LocalDateTime;

public record AuthenticationResult(
        AuthenticationResponse response,
        String refreshToken,
        boolean remembered,
        LocalDateTime refreshExpiresAt
) {
}
