package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.repository.PriceHistoryRepository;
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

    /**
     * How many days of price history to retain.
     * Configurable via price.history.retention-days in application.properties (default: 90).
     */
    @Value("${price.history.retention-days:90}")
    private int historyRetentionDays;

    /**
     * Scheduled task: update prices for all tracked items.
     * Rate and initial delay are configurable via application.properties:
     *   price.update.rate-ms        (default: 43 200 000 ms = 12 hours)
     *   price.update.initial-delay-ms (default: 60 000 ms = 1 min)
     */
    @Scheduled(
        fixedRateString  = "${price.update.rate-ms:43200000}",
        initialDelayString = "${price.update.initial-delay-ms:60000}"
    )
    public void scheduledPriceUpdate() {
        log.info("Starting scheduled price update for all tracked items");
        try {
            steamPriceService.updateAllTrackedItems();
            log.info("Scheduled price update completed successfully");
        } catch (Exception e) {
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
            log.info("PriceHistory prune completed — {} records deleted", deleted);
        } catch (Exception e) {
            log.error("Error during PriceHistory prune: {}", e.getMessage(), e);
        }
    }
}
