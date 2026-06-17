package com.smarteventbar.notification.properties;

import com.smarteventbar.config.NotificationConfig;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Preservation property-based tests that verify baseline behavior on UNFIXED code.
 * These tests MUST PASS on the current codebase to confirm the behavior we want to preserve
 * after applying the fix.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 */
class NotificationPreservationProperties {

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(5).ofMaxLength(14).map(rest ->
                        "+" + firstDigit + rest
                )
        );
    }

    @Provide
    Arbitrary<String> providerMessageIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(50)
                .map(s -> "wamid." + s);
    }

    @Provide
    Arbitrary<Long> orderIds() {
        return Arbitraries.longs().between(1L, 100000L);
    }

    @Provide
    Arbitrary<String> visualOrderNumbers() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(10);
    }

    private CustomerOrder createNonOptedInOrder(Long id, String phone, String visualOrderNumber) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(false); // NOT opted in
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(29.99));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /**
     * Property 2a: Production executor type is ThreadPoolTaskExecutor.
     *
     * Verifies that the production NotificationConfig.notificationExecutor() bean
     * returns a ThreadPoolTaskExecutor (not a SyncTaskExecutor). This ensures the
     * production async behavior is preserved after the test-only fix is applied.
     *
     * Uses @Example since this is a deterministic assertion with no randomized inputs.
     *
     * Validates: Requirements 3.1
     */
    @Example
    void productionExecutorIsThreadPoolTaskExecutor() {
        NotificationConfig config = new NotificationConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.notificationExecutor();
        try {
            assertThat(executor).isNotNull();
            assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Creates a NotificationService backed by the provided mocks with the channel enabled.
     */
    private NotificationService createServiceWithMocks(
            NotificationChannel channel,
            NotificationLogRepository logRepo,
            OrderRepository orderRepo) {
        when(channel.isEnabled()).thenReturn(true);
        return new NotificationService(logRepo, Optional.of(channel), orderRepo);
    }

    /**
     * Property 2b: Non-opted-in orders create no notification log entry.
     *
     * For random phone numbers with whatsappOptIn=false, calling notifyOrderConfirmed
     * should NOT create any notification log entry and should NOT call the channel.
     *
     * Validates: Requirements 3.2
     */
    @Property
    void nonOptedInOrdersCreateNoNotificationLogEntry(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);

        NotificationService service = createServiceWithMocks(channel, logRepo, orderRepo);

        CustomerOrder order = createNonOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderConfirmed(order);

        // Verify no log entry is saved
        verify(logRepo, never()).save(any(NotificationLog.class));
        // Verify channel is never called
        verify(channel, never()).sendOrderConfirmed(any());
    }

    /**
     * Property 2c: Unknown provider message IDs handled gracefully.
     *
     * For random provider message ID strings, processDeliveryStatus should NOT throw
     * when the message ID is not found in the repository.
     *
     * Validates: Requirements 3.4
     */
    @Property
    void unknownProviderMessageIdsHandledGracefully(
            @ForAll("providerMessageIds") String providerMessageId) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        OrderRepository orderRepo = mock(OrderRepository.class);

        when(logRepo.findByProviderMessageId(providerMessageId)).thenReturn(Optional.empty());

        NotificationService service = createServiceWithMocks(channel, logRepo, orderRepo);

        // Should not throw for unknown message IDs
        assertThatCode(() -> service.processDeliveryStatus(providerMessageId, "delivered"))
                .doesNotThrowAnyException();
    }
}
