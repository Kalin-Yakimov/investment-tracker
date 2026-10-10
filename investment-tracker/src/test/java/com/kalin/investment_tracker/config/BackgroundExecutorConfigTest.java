package com.kalin.investment_tracker.config;

import com.kalin.investment_tracker.email.VerificationEmailConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundExecutorConfigTest {
    @Test void applicationAndEmailQueuesAreSeparateAndApplicationQueueIsBounded() throws Exception {
        var settings = new ResourcePropertySource("classpath:application.properties");
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
                .withUserConfiguration(VerificationEmailConfig.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(settings))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var app = context.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
                    var mail = context.getBean("verificationEmailExecutor", ThreadPoolTaskExecutor.class);
                    assertNotSame(app, mail);
                    assertEquals(2, app.getCorePoolSize()); assertEquals(4, app.getMaxPoolSize());
                    assertEquals(50, app.getQueueCapacity()); assertEquals(100, mail.getQueueCapacity());
                    CountDownLatch started = new CountDownLatch(4), release = new CountDownLatch(1);
                    Runnable blocking = () -> {
                        started.countDown();
                        try { release.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    };
                    try {
                        for (int i = 0; i < 54; i++) app.execute(blocking);
                        assertTrue(started.await(5, TimeUnit.SECONDS));
                        assertEquals(50, app.getThreadPoolExecutor().getQueue().size());
                        assertThrows(RejectedExecutionException.class, () -> app.execute(blocking));
                    } finally { release.countDown(); }
                });
    }
}
