package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.admin.SystemJobStatusService;
import com.kalin.investment_tracker.repository.PriceHistoryRepository;
import com.kalin.investment_tracker.repository.MarketPriceSnapshotRepository;
import com.kalin.investment_tracker.service.SteamPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledPriceUpdateConfig {

    private final SteamPriceService steamPriceService;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketPriceSnapshotRepository marketPriceSnapshotRepository;
    private final SystemJobStatusService jobStatusService;

    /**
     * How many days of price history to retain.
     * Configurable via price.history.retention-days in application.properties (default: 90).
     */
    @Value("${price.history.retention-days:90}")
    private int historyRetentionDays;

    @Value("${price.snapshot.retention-days:120}")
    private int snapshotRetentionDays;

    /**
     * Scheduled task: update prices for all tracked items.
     * Rate and initial delay are configurable via application.properties:
     *   price.update.rate-ms        (default: 7 200 000 ms = 2 hours)
     *   price.update.initial-delay-ms (default: 5 000 ms = 5 sec)
     */
    @Scheduled(
        fixedDelayString = "${price.update.rate-ms:7200000}",
        initialDelayString = "${price.update.initial-delay-ms:5000}"
    )
    public void scheduledPriceUpdate() {
        log.info("Starting scheduled price update for all tracked items");
        jobStatusService.started(SystemJobStatusService.PRICE_UPDATE, "Scheduled price update is running");
        try {
            int recordedSnapshots = steamPriceService.updateAllTrackedItems();
            jobStatusService.succeeded(
                    SystemJobStatusService.PRICE_UPDATE,
                    recordedSnapshots > 0
                            ? "Recorded " + recordedSnapshots + " new price observations"
                            : "No new price observations were available from CSGOTrader"
            );
            log.info("Scheduled price update completed; {} new observations", recordedSnapshots);
        } catch (Exception e) {
            jobStatusService.failed(SystemJobStatusService.PRICE_UPDATE, e.getMessage());
            log.error("Error during scheduled price update: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily auto-prune: delete PriceHistory entries older than the configured retention period.
     * Runs every day at 03:00 to keep the table bounded without impacting daytime usage.
     * Configurable via price.history.retention-days (default: 90 days).
     */
    @Scheduled(cron = "${price.history.prune-cron:0 0 3 * * ?}")
    @Transactional
    public void pruneOldPriceHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(historyRetentionDays);
        log.info("Starting daily PriceHistory prune — removing entries older than {} days (before {})",
                historyRetentionDays, cutoff);
        try {
            int deleted = priceHistoryRepository.deleteByDateExtractedBefore(cutoff);
            int deletedSnapshots = marketPriceSnapshotRepository.deleteByObservedAtBefore(
                    java.time.Instant.now().minus(java.time.Duration.ofDays(snapshotRetentionDays))
            );
            log.info("Price history prune completed — {} legacy records and {} market snapshots deleted",
                    deleted, deletedSnapshots);
        } catch (Exception e) {
            log.error("Error during PriceHistory prune: {}", e.getMessage(), e);
        }
    }
}
