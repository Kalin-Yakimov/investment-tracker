package com.kalin.investment_tracker.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;
    private long accessExpirationMinutes = 30;
    private String issuer = "investment-tracker";
    private String audience = "investment-tracker-web";
}
