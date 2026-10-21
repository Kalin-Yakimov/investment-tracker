package com.kalin.investment_tracker.validation;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ApiRequestBudgetTest {
    @Test void accountBudgetsAreIndependentAndResetOnMonotonicTime() {
        var nanos = new AtomicLong();
        var limiter = new ApiRequestBudget(new ResourceLimitsProperties(), nanos::get);
        limiter.consume("user:1:inventory", 2); limiter.consume("user:1:inventory", 2);
        assertEquals(60, assertThrows(ResourceLimitException.class, () -> limiter.consume("user:1:inventory", 2)).getRetryAfterSeconds());
        limiter.consume("user:2:inventory", 2);
        nanos.set(Duration.ofSeconds(59).toNanos());
        assertEquals(1, assertThrows(ResourceLimitException.class, () -> limiter.consume("user:1:inventory", 2)).getRetryAfterSeconds());
        nanos.set(Duration.ofMinutes(1).toNanos()); limiter.consume("user:1:inventory", 2);
    }
    @Test void fullIdentityRegistryDoesNotEvictAnExistingBudgetToAdmitNewKeys() {
        var limits = new ResourceLimitsProperties(); limits.setMaximumRateLimitEntries(2);
        var nanos = new AtomicLong(); var limiter = new ApiRequestBudget(limits, nanos::get);
        limiter.consume("first", 1); limiter.consume("second", 1);
        assertEquals(503, assertThrows(ResourceLimitException.class, () -> limiter.consume("third", 1)).getStatus().value());
        assertEquals(429, assertThrows(ResourceLimitException.class, () -> limiter.consume("first", 1)).getStatus().value());
        nanos.set(Duration.ofMinutes(1).toNanos()); limiter.consume("third", 1);
    }
    @Test void overlappingRequestsCannotOverspendOneWindow() throws Exception {
        var limiter = new ApiRequestBudget(new ResourceLimitsProperties());
        try (var workers = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 100).<Callable<Boolean>>mapToObj(index -> () -> {
                try { limiter.consume("one-account", 25); return true; }
                catch (ResourceLimitException exhausted) { return false; }
            }).toList();
            int accepted = 0;
            for (var future : workers.invokeAll(tasks)) if (future.get(5, TimeUnit.SECONDS)) accepted++;
            assertEquals(25, accepted);
        }
    }
}
