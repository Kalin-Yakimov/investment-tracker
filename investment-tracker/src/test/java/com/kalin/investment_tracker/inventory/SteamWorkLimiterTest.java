package com.kalin.investment_tracker.inventory;

import com.kalin.investment_tracker.validation.ResourceLimitException;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SteamWorkLimiterTest {
    @Test void allProfilesShareOneRequestStartScheduleWithoutRealSleeping() {
        var clock = new AtomicLong(); var settings = new SteamInventoryProperties();
        var limiter = new SteamWorkLimiter(settings, clock::get, clock::addAndGet);
        var starts = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) limiter.request(() -> { starts.add(clock.get()); return "reply"; });
        assertEquals(java.util.List.of(0L, 1000000000L, 2000000000L), starts);
    }
    @Test void overlappingCalculationsFailFastAndReleaseCapacityAfterCompletion() throws Exception {
        var settings = new SteamInventoryProperties(); settings.setMaximumConcurrentCalculations(1);
        var limiter = new SteamWorkLimiter(settings);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            var first = worker.submit(() -> limiter.calculate(() -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test release missing"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return 1;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(503, assertThrows(ResourceLimitException.class, () -> limiter.calculate(() -> 2)).getStatus().value());
            } finally { release.countDown(); }
            assertEquals(1, first.get(5, TimeUnit.SECONDS));
        }
        assertEquals(3, limiter.calculate(() -> 3));
    }
    @Test void failedAndInterruptedWorkNeverLeaksPermits() {
        var settings = new SteamInventoryProperties(); settings.setMaximumConcurrentCalculations(1); settings.setMaximumConcurrentRequests(1);
        var clock = new AtomicLong();
        var limiter = new SteamWorkLimiter(settings, clock::get, nanos -> { throw new InterruptedException(); });
        assertThrows(IllegalStateException.class, () -> limiter.calculate(() -> { throw new IllegalStateException(); }));
        assertEquals("released", limiter.calculate(() -> "released"));
        limiter.request(() -> "first");
        try {
            assertThrows(ResourceLimitException.class, () -> limiter.request(() -> "not executed"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        clock.set(TimeUnit.SECONDS.toNanos(10));
        assertEquals("recovered", limiter.request(() -> "recovered"));
    }
    @Test void lookupAdmissionAlsoBoundsThreadsWaitingForOneCachedProfile() {
        var settings = new SteamInventoryProperties(); settings.setMaximumConcurrentLookups(1);
        var limiter = new SteamWorkLimiter(settings);
        assertEquals("finished", limiter.lookup(() -> {
            assertThrows(ResourceLimitException.class, () -> limiter.lookup(() -> "cannot queue"));
            return "finished";
        }));
        assertEquals("next", limiter.lookup(() -> "next"));
    }
}
