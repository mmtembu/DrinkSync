package com.smarteventbar.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for the notification subsystem.
 * Provides an async thread pool for non-blocking notification dispatch
 * and a rate-limited send queue to stay within Meta's API rate limits.
 */
@Configuration
@EnableAsync
public class NotificationConfig {

    /**
     * Async thread pool for notification dispatch.
     * Core pool: 2 threads, Max pool: 5 threads, Queue capacity: 100.
     * Notifications are dispatched on this executor to avoid blocking order state transitions.
     */
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Rate-limited send queue with token-bucket algorithm.
     * Default: 50 messages/second max throughput, queue capacity of 100.
     * The rate is configurable via the whatsapp.max-send-rate-per-second property.
     */
    @Bean
    public RateLimitedSendQueue rateLimitedSendQueue(
            @Value("${whatsapp.max-send-rate-per-second:50}") int maxSendRatePerSecond,
            MeterRegistry meterRegistry) {
        RateLimitedSendQueue queue = new RateLimitedSendQueue(100, maxSendRatePerSecond);

        Gauge.builder("whatsapp.queue.depth", queue, RateLimitedSendQueue::getQueueDepth)
                .description("Current number of outbound WhatsApp messages waiting in the send queue")
                .register(meterRegistry);

        return queue;
    }
}
