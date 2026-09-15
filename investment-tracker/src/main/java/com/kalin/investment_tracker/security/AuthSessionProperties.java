package com.kalin.investment_tracker.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.auth")
public class AuthSessionProperties {

    private long rememberedRefreshDays = 60;
    private long sessionRefreshHours = 24;
    private String refreshCookieName = "refresh_token";
    private boolean cookieSecure = false;
    private String cookieSameSite = "Strict";
    private long expiredSessionRetentionDays = 7;
}
