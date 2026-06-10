package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for the opt-in gate in NotificationService.
 *
 * Property 4: Opt-in gate — no notification without consent
 * For any order with whatsappOptIn set to false, transitioning to any notification-triggering
 * state (PAID, READY, COLLECTED) SHALL result in zero outbound WhatsApp messages and zero
 * notification log entries for that order.
 *
 * Validates: Requirements 1.7, 2.5
 */
class OptInGateProperties {

    @Provide
    Arbitrary<String> phoneNumbers() {
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(5).ofMaxLength(14).map(rest ->
                        "+" + firstDigit + rest
                )
        );
    }

    @Provide
    Arbitrary<Long> orderIds() {
        return Arbitraries.longs().between(1L, 100000L);
    }

    @Provide
    Arbitrary<String> visualOrderNumbers() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(3).ofMaxLength(10);
    }

    private CustomerOrder createOptedOutOrder(Long id, String phone, String visualOrderNumber) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(false); // Explicitly opted out
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(99.99));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /**
     * Property 4a: Order with opt-in=false triggers zero messages on notifyOrderConfirmed.
     */
    @Property
    void optedOutOrderTriggersZeroMessagesOnConfirmed(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        // Setup mocks
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        OrderRepository orderRepo = mock(OrderRepository.class);

        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedOutOrder(orderId, phone, visualOrderNumber);

        // Act
        service.notifyOrderConfirmed(order);

        // Assert: channel never called, no log entries saved
        verifyNoInteractions(channel);
        verify(logRepo, never()).save(any(NotificationLog.class));
    }

    /**
     * Property 4b: Order with opt-in=false triggers zero messages on notifyOrderReady.
     */
    @Property
    void optedOutOrderTriggersZeroMessagesOnReady(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        OrderRepository orderRepo = mock(OrderRepository.class);

        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedOutOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderReady(order);

        verifyNoInteractions(channel);
        verify(logRepo, never()).save(any(NotificationLog.class));
    }

    /**
     * Property 4c: Order with opt-in=false triggers zero messages on notifyOrderCollected.
     */
    @Property
    void optedOutOrderTriggersZeroMessagesOnCollected(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        OrderRepository orderRepo = mock(OrderRepository.class);

        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedOutOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderCollected(order);

        verifyNoInteractions(channel);
        verify(logRepo, never()).save(any(NotificationLog.class));
    }

    /**
     * Property 4d: Order with opt-in=false triggers zero messages regardless of which
     * notification method is called (combined property).
     */
    @Property
    void optedOutOrderNeverTriggersAnyNotification(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll @IntRange(min = 0, max = 2) int methodIndex) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        OrderRepository orderRepo = mock(OrderRepository.class);

        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedOutOrder(orderId, phone, visualOrderNumber);

        // Call one of the three notification methods based on generated index
        switch (methodIndex) {
            case 0 -> service.notifyOrderConfirmed(order);
            case 1 -> service.notifyOrderReady(order);
            case 2 -> service.notifyOrderCollected(order);
        }

        // Assert: zero outbound messages and zero log entries
        verifyNoInteractions(channel);
        verify(logRepo, never()).save(any(NotificationLog.class));
    }
}
