package com.kalin.investment_tracker.validation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/** Monotonic fixed windows. Full registries refuse new keys, never evict a live security budget. */
@Service
public class ApiRequestBudget {
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();
    private final Cache<String, Window> windows;
    private final ResourceLimitsProperties limits;
    private final Ticker ticker;

    @Autowired
    public ApiRequestBudget(ResourceLimitsProperties limits) { this(limits, Ticker.systemTicker()); }

    ApiRequestBudget(ResourceLimitsProperties limits, Ticker ticker) {
        this.limits = limits; this.ticker = ticker;
        windows = Caffeine.newBuilder().maximumSize(limits.getMaximumRateLimitEntries())
                .expireAfterWrite(Duration.ofMinutes(1)).ticker(ticker).build();
    }

    public synchronized void consume(String identity, int limit) {
        String key = hash(identity);
        long now = ticker.read();
        Window window = windows.getIfPresent(key);
        if (window == null) {
            windows.cleanUp();
            if (windows.asMap().size() >= limits.getMaximumRateLimitEntries()) {
                throw new ResourceLimitException(HttpStatus.SERVICE_UNAVAILABLE, "Request protection is busy. Please try again shortly.", 60);
            }
        }
        if (window == null || now - window.start() >= WINDOW_NANOS) window = new Window(now, 0);
        if (window.count() >= limit) {
            long remaining = WINDOW_NANOS - (now - window.start());
            throw new ResourceLimitException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait before trying again.",
                    Math.max(1, (remaining + 999999999L) / 1000000000L));
        }
        windows.put(key, new Window(window.start(), window.count() + 1));
    }

    private String hash(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private record Window(long start, int count) { }
}
