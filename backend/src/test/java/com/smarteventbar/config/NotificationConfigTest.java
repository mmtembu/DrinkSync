package com.smarteventbar.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NotificationConfig}.
 * Verifies that the whatsapp.queue.depth gauge metric is registered
 * and correctly reads from the RateLimitedSendQueue.
 */
class NotificationConfigTest {

    private MeterRegistry meterRegistry;
    private NotificationConfig config;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        config = new NotificationConfig();
    }

    @Test
    void rateLimitedSendQueue_registersQueueDepthGauge() {
        RateLimitedSendQueue queue = config.rateLimitedSendQueue(50, meterRegistry);

        Gauge gauge = meterRegistry.find("whatsapp.queue.depth").gauge();

        assertNotNull(gauge, "whatsapp.queue.depth gauge should be registered");
        assertEquals(0.0, gauge.value(), "Queue depth should be 0 when queue is empty");
    }

    @Test
    void queueDepthGauge_reflectsCurrentQueueSize() {
        RateLimitedSendQueue queue = config.rateLimitedSendQueue(50, meterRegistry);

        Gauge gauge = meterRegistry.find("whatsapp.queue.depth").gauge();
        assertNotNull(gauge);

        // The gauge reads from getQueueDepth() which returns queue.size()
        // Since the queue processes tasks immediately on enqueue, we verify the gauge
        // is wired correctly by checking it returns the same value as getQueueDepth()
        assertEquals(queue.getQueueDepth(), (int) gauge.value());
    }

    @Test
    void queueDepthGauge_hasDescription() {
        config.rateLimitedSendQueue(50, meterRegistry);

        Gauge gauge = meterRegistry.find("whatsapp.queue.depth").gauge();

        assertNotNull(gauge);
        assertNotNull(gauge.getId().getDescription());
        assertTrue(gauge.getId().getDescription().contains("queue"));
    }
}
