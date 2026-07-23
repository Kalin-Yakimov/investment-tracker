package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.service.SteamPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ScheduledPriceUpdateConfig {

    private final SteamPriceService steamPriceService;

    /**
     * Scheduled task to update prices for all tracked items every 30 minutes
     * Runs every 30 minutes (1800000 ms)
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 60000)
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
     * Alternative: Use cron expression for more precise scheduling
     * This runs every 30 minutes at the top and bottom of the hour
     */
    // @Scheduled(cron = "0 0,30 * * * ?")
    // public void scheduledPriceUpdateCron() {
    //     log.info("Starting scheduled price update (cron) for dashboard items");
    //     try {
    //         steamPriceService.updateAllDashboardItems();
    //         log.info("Scheduled price update (cron) completed successfully");
    //     } catch (Exception e) {
    //         log.error("Error during scheduled price update (cron): {}", e.getMessage(), e);
    //     }
    // }
}
