package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for notification deduplication.
 *
 * Property 13: Deduplication prevents duplicate sends
 * For any order that already has a notification log entry with the same message type and a
 * status of "sent", "delivered", or "read", attempting to send the same message type SHALL
 * be skipped (no HTTP request made). If the existing entry has status "failed", the send
 * SHALL proceed.
 *
 * Validates: Requirements 15.1, 15.2, 15.3
 */
class DeduplicationProperties {

    private static final List<String> DEDUP_STATUSES = Arrays.asList("sent", "delivered", "read");
    private static final String MSG_TYPE = "order_confirmed";

    @Provide
    Arbitrary<String> dedupStatuses() {
        return Arbitraries.of("sent", "delivered", "read");
    }

    @Provide
    Arbitrary<Long> orderIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    /**
     * Property 13a: When a notification log entry exists with status "sent", "delivered", or "read",
     * the channel is NOT called (deduplication prevents duplicate send).
     */
    @Property(tries = 100)
    void existingSuccessfulLogEntryPreventsResend(
            @ForAll("dedupStatuses") String existingStatus,
            @ForAll("orderIds") Long orderId) {

        // Setup mocks
        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(channel.isEnabled()).thenReturn(true);

        // Create an order with opt-in enabled
        CustomerOrder order = new CustomerOrder();
        order.setId(orderId);
        order.setWhatsappOptIn(true);
        order.setCustomerPhone("+27123456789");

        // Mock existing log entry with a dedup status
        NotificationLog existingLog = new NotificationLog();
        existingLog.setStatus(existingStatus);
        when(logRepository.findByOrderIdAndMessageTypeAndStatusIn(
                eq(orderId), eq(MSG_TYPE), eq(DEDUP_STATUSES)))
                .thenReturn(Optional.of(existingLog));

        // Create service with the mocked channel
        NotificationService service = new NotificationService(
                logRepository, Optional.of(channel), orderRepository);

        // Trigger notification
        service.notifyOrderConfirmed(order);

        // Verify channel was NOT called (deduplication)
        verify(channel, never()).sendOrderConfirmed(any());
    }

    /**
     * Property 13b: When a notification log entry exists with status "failed",
     * the channel IS called (retry is allowed).
     */
    @Property(tries = 100)
    void failedLogEntryAllowsRetry(@ForAll("orderIds") Long orderId) {

        // Setup mocks
        NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
        NotificationChannel channel = mock(NotificationChannel.class);
        OrderRepository orderRepository = mock(OrderRepository.class);

        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenReturn("wamid.retry123");

        // Create an order with opt-in enabled
        CustomerOrder order = new CustomerOrder();
        order.setId(orderId);
        order.setWhatsappOptIn(true);
        order.setCustomerPhone("+27123456789");

        // Mock: no existing dedup entry (failed entries are not in the dedup statuses list)
        when(logRepository.findByOrderIdAndMessageTypeAndStatusIn(
                eq(orderId), eq(MSG_TYPE), eq(DEDUP_STATUSES)))
                .thenReturn(Optional.empty());

        // Mock save to return the log entry
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            log.setId(1L);
            return log;
        });

        // Create service with the mocked channel
        NotificationService service = new NotificationService(
                logRepository, Optional.of(channel), orderRepository);

        // Trigger notification
        service.notifyOrderConfirmed(order);

        // Verify channel WAS called (retry allowed)
        verify(channel, times(1)).sendOrderConfirmed(order);
    }
}
