package com.kalin.investment_tracker.inventory;

import com.github.benmanes.caffeine.cache.Ticker;
import com.kalin.investment_tracker.validation.ResourceLimitException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** One bounded workload and request-start schedule shared by all public Steam profiles. */
@Component
public class SteamWorkLimiter {
    @FunctionalInterface interface Sleeper { void sleep(long nanos) throws InterruptedException; }
    private final Semaphore calculations;
    private final Semaphore lookups;
    private final Semaphore requests;
    private final SteamInventoryProperties settings;
    private final Ticker ticker;
    private final Sleeper sleeper;
    private long nextStart;
    private boolean scheduled;

    @Autowired
    public SteamWorkLimiter(SteamInventoryProperties settings) {
        this(settings, Ticker.systemTicker(), nanos -> TimeUnit.NANOSECONDS.sleep(nanos));
    }
    SteamWorkLimiter(SteamInventoryProperties settings, Ticker ticker, Sleeper sleeper) {
        this.settings = settings; this.ticker = ticker; this.sleeper = sleeper;
        calculations = new Semaphore(settings.getMaximumConcurrentCalculations());
        lookups = new Semaphore(settings.getMaximumConcurrentLookups());
        requests = new Semaphore(settings.getMaximumConcurrentRequests());
    }
    public <T> T calculate(Supplier<T> calculation) {
        return withPermit(calculations, calculation);
    }
    /** Bounds cache single-flight waiters and vanity lookups as well as actual downloads. */
    public <T> T lookup(Supplier<T> lookup) { return withPermit(lookups, lookup); }
    private <T> T withPermit(Semaphore slots, Supplier<T> action) {
        if (!slots.tryAcquire()) throw busy();
        try { return action.get(); }
        finally { slots.release(); }
    }
    public <T> T request(Supplier<T> action) {
        if (!requests.tryAcquire()) throw busy();
        try {
            long delay;
            synchronized (this) {
                long now = ticker.read();
                delay = scheduled ? Math.max(0, nextStart - now) : 0;
                if (delay > TimeUnit.MILLISECONDS.toNanos(settings.getMaximumRequestWaitMs())) throw busy();
                nextStart = now + delay + TimeUnit.MILLISECONDS.toNanos(settings.getMinimumRequestIntervalMs());
                scheduled = true;
            }
            if (delay > 0) sleeper.sleep(delay);
            return action.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResourceLimitException(HttpStatus.SERVICE_UNAVAILABLE, "Steam loading was interrupted. Please try again shortly.", 2);
        } finally { requests.release(); }
    }
    private ResourceLimitException busy() {
        return new ResourceLimitException(HttpStatus.SERVICE_UNAVAILABLE, "The inventory calculator is busy. Please try again shortly.", 2);
    }
}
