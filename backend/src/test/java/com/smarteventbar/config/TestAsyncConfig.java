package com.smarteventbar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Test-only configuration that provides the notificationExecutor bean as a
 * SyncTaskExecutor. This makes @Async notification methods execute inline on
 * the calling thread, within the same transaction as the test, eliminating
 * race conditions and transaction isolation issues in integration tests.
 *
 * The production NotificationConfig uses @ConditionalOnMissingBean so it
 * backs off when this bean is already defined.
 */
@Configuration
@Profile("test")
public class TestAsyncConfig {

    @Bean("notificationExecutor")
    public Executor notificationExecutor() {
        return new SyncTaskExecutor();
    }
}
