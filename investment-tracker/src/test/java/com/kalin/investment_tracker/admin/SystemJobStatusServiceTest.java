package com.kalin.investment_tracker.admin;

import com.kalin.investment_tracker.config.ScheduledPriceUpdateConfig;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.service.SteamPriceService;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemJobStatusServiceTest {
    @Test void simultaneousAttemptsClaimTheJobOnlyOnce() throws Exception {
        var status = new SystemJobStatusService();
        try (var workers = Executors.newFixedThreadPool(8)) {
            CountDownLatch start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) futures.add(workers.submit(() -> {
                start.await(); return status.tryStart(SystemJobStatusService.PRICE_UPDATE, "running");
            }));
            start.countDown(); int claimed = 0;
            for (var result : futures) if (result.get(5, TimeUnit.SECONDS)) claimed++;
            assertEquals(1, claimed);
        }
    }
    @Test void failedAttemptPreservesLastSuccessfulCompletionAndReleasesJob() {
        var status = new SystemJobStatusService(); String job = SystemJobStatusService.PRICE_UPDATE;
        assertTrue(status.tryStart(job, "first")); status.succeeded(job, "done");
        var completed = status.get(job).completedAt(); assertNotNull(completed);
        assertTrue(status.tryStart(job, "second")); assertEquals(completed, status.get(job).completedAt());
        status.failed(job, "failed"); assertEquals(completed, status.get(job).completedAt());
        assertTrue(status.tryStart(job, "third"));
    }
    @Test void firstFailureDoesNotClaimThatAnyJobCompletedSuccessfully() {
        var status = new SystemJobStatusService(); String job = SystemJobStatusService.CATALOGUE_SYNC;
        status.tryStart(job, "running"); status.failed(job, "failed");
        assertNull(status.get(job).completedAt()); assertEquals("FAILED", status.get(job).status());
    }
    @Test void schedulerDoesNotOverlapManualPriceJob() {
        var status = new SystemJobStatusService(); var prices = mock(SteamPriceService.class);
        status.tryStart(SystemJobStatusService.PRICE_UPDATE, "manual");
        var scheduler = new ScheduledPriceUpdateConfig(prices, mock(PriceHistoryRepository.class), mock(MarketPriceSnapshotRepository.class), status);
        scheduler.scheduledPriceUpdate(); verifyNoInteractions(prices);
    }
    @Test void schedulerFailureIsReportedWithoutFalseCompletion() {
        var status = new SystemJobStatusService(); var prices = mock(SteamPriceService.class);
        when(prices.updateAllTrackedItems()).thenThrow(new IllegalStateException("confidential response"));
        var scheduler = new ScheduledPriceUpdateConfig(prices, mock(PriceHistoryRepository.class), mock(MarketPriceSnapshotRepository.class), status);
        scheduler.scheduledPriceUpdate();
        var result = status.get(SystemJobStatusService.PRICE_UPDATE);
        assertEquals("FAILED", result.status()); assertNull(result.completedAt());
        assertFalse(result.message().contains("confidential"));
    }
}
