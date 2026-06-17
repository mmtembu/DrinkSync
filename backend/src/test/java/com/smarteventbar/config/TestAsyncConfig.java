package com.smarteventbar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Test-only configuration that overrides the notificationExecutor bean
 * with a SyncTaskExecutor. This makes @Async notification methods execute
 * inline on the calling thread, within the same transaction as the test,
 * eliminating race conditions and transaction isolation issues in integration tests.
 *
 * Uses @Primary to guarantee this bean wins over the production NotificationConfig
 * bean regardless of registration order.
 */
@Configuration
@Profile("test")
public class TestAsyncConfig {

    @Primary
    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        return new SyncTaskExecutor();
    }
}
