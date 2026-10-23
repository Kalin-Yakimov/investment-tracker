package com.kalin.investment_tracker.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class VerificationEmailConfig {
    @Bean
    public Clock verificationClock() {
        // Keep compatibility with existing LocalDateTime code expiry values.
        return Clock.systemDefaultZone();
    }

    @Bean(name = "verificationEmailExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor verificationEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("verification-email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
