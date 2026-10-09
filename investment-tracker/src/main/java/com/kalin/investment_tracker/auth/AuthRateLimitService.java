package com.kalin.investment_tracker.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.kalin.investment_tracker.user.TokenType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/** Single-instance limits. A shared limiter is required before multi-instance deployment. */
@Service
public class AuthRateLimitService {
    public enum Operation { REGISTER, LOGIN, REQUEST_CODE, VERIFY_CODE }

    private final AuthProtectionProperties properties;
    private final Cache<String, Window> windows;
    private final Ticker ticker;

    @Autowired
    public AuthRateLimitService(AuthProtectionProperties properties) {
        this(properties, Ticker.systemTicker());
    }

    AuthRateLimitService(AuthProtectionProperties properties, Ticker ticker) {
        this.properties = properties;
        this.ticker = ticker;
        this.windows = Caffeine.newBuilder()
                .maximumSize(properties.getMaximumLimitEntries())
                .expireAfterWrite(properties.getRequestWindow().compareTo(properties.getAttemptWindow()) >= 0
                        ? properties.getRequestWindow() : properties.getAttemptWindow())
                .ticker(ticker)
                .build();
    }

    public void checkIp(Operation operation, String ipAddress) {
        String ip = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
        int limit = switch (operation) {
            case REGISTER -> properties.getRegistrationsPerIp();
            case LOGIN -> properties.getLoginsPerIp();
            case REQUEST_CODE -> properties.getRequestsPerIp();
            case VERIFY_CODE -> properties.getAttemptsPerIp();
        };
        Duration duration = operation == Operation.REGISTER || operation == Operation.REQUEST_CODE
                ? properties.getRequestWindow() : properties.getAttemptWindow();
        consume("ip:" + operation + ":" + ip, limit, duration, Duration.ZERO);
    }

    public void checkLogin(String email) {
        consume("login:" + normalizeEmail(email), properties.getLoginsPerEmail(),
                properties.getAttemptWindow(), Duration.ZERO);
    }

    public void checkLoginForUser(Integer userId) {
        consume("login:user:" + userId, properties.getLoginsPerEmail(),
                properties.getAttemptWindow(), Duration.ZERO);
    }

    public void checkCodeRequest(String email) {
        // Activation and password-reset requests share an email sending budget.
        consume("issue:" + normalizeEmail(email), properties.getRequestsPerEmail(),
                properties.getRequestWindow(), properties.getResendCooldown());
    }

    public void checkCodeRequestForUser(Integer userId) {
        consume("issue:user:" + userId, properties.getRequestsPerEmail(),
                properties.getRequestWindow(), properties.getResendCooldown());
    }

    public int checkVerification(String email, TokenType type) {
        return consume("verify:" + type + ":" + normalizeEmail(email), properties.getAttemptsPerEmail(),
                properties.getAttemptWindow(), Duration.ZERO);
    }

    public int checkVerificationForUser(Integer userId, TokenType type) {
        return consume("verify:" + type + ":user:" + userId, properties.getAttemptsPerEmail(),
                properties.getAttemptWindow(), Duration.ZERO);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private synchronized int consume(String key, int limit, Duration duration, Duration cooldown) {
        String hashedKey = hash(key);
        long now = ticker.read();
        Window current = windows.getIfPresent(hashedKey);
        if (current == null || now - current.startedAt() >= duration.toNanos()) {
            windows.cleanUp();
            // Do not evict a live security budget to admit a new identity.
            if (current == null && windows.asMap().size() >= properties.getMaximumLimitEntries()) {
                throw new AuthRateLimitException(duration.toSeconds());
            }
            current = new Window(now, now, 0);
        }
        if (current.count() >= limit) {
            throw new AuthRateLimitException(secondsRemaining(duration.toNanos() - (now - current.startedAt())));
        }
        if (current.count() > 0 && now - current.lastAcceptedAt() < cooldown.toNanos()) {
            throw new AuthRateLimitException(secondsRemaining(cooldown.toNanos() - (now - current.lastAcceptedAt())));
        }
        int count = current.count() + 1;
        windows.put(hashedKey, new Window(current.startedAt(), now, count));
        return count;
    }

    private long secondsRemaining(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    private String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Window(long startedAt, long lastAcceptedAt, int count) { }
}
