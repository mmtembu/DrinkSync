package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for state transition → notification type mapping.
 *
 * Property 5: State transition triggers correct notification type
 * For any order with whatsappOptIn set to true and a valid phone number, transitioning to
 * PAID SHALL trigger an order_confirmed message, transitioning to READY SHALL trigger an
 * order_ready message, and transitioning to COLLECTED SHALL trigger an order_receipt message
 * — each sent to the order's customerPhone.
 *
 * Validates: Requirements 2.1, 3.1, 4.1
 */
class StateTransitionNotificationProperties {

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

    private CustomerOrder createOptedInOrder(Long id, String phone, String visualOrderNumber) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(true); // Opted in
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(49.99));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private NotificationService createServiceWithMocks(
            NotificationChannel channel, NotificationLogRepository logRepo) {
        when(channel.isEnabled()).thenReturn(true);
        // No dedup match — allow send
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        // Return the log entry on save (simulate persistence)
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(1L);
            }
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        return new NotificationService(logRepo, Optional.of(channel), orderRepo);
    }

    /**
     * Property 5a: notifyOrderConfirmed calls sendOrderConfirmed on the channel.
     */
    @Property
    void confirmedStateTriggersOrderConfirmedMessage(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        NotificationService service = createServiceWithMocks(channel, logRepo);

        when(channel.sendOrderConfirmed(any(CustomerOrder.class)))
                .thenReturn(UUID.randomUUID().toString());

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderConfirmed(order);

        verify(channel).sendOrderConfirmed(order);
        verify(channel, never()).sendOrderReady(any());
        verify(channel, never()).sendReceipt(any());
    }

    /**
     * Property 5b: notifyOrderReady calls sendOrderReady on the channel.
     */
    @Property
    void readyStateTriggersOrderReadyMessage(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        NotificationService service = createServiceWithMocks(channel, logRepo);

        when(channel.sendOrderReady(any(CustomerOrder.class)))
                .thenReturn(UUID.randomUUID().toString());

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderReady(order);

        verify(channel).sendOrderReady(order);
        verify(channel, never()).sendOrderConfirmed(any());
        verify(channel, never()).sendReceipt(any());
    }

    /**
     * Property 5c: notifyOrderCollected calls sendReceipt on the channel.
     */
    @Property
    void collectedStateTriggersReceiptMessage(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        NotificationService service = createServiceWithMocks(channel, logRepo);

        when(channel.sendReceipt(any(CustomerOrder.class)))
                .thenReturn(UUID.randomUUID().toString());

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderCollected(order);

        verify(channel).sendReceipt(order);
        verify(channel, never()).sendOrderConfirmed(any());
        verify(channel, never()).sendOrderReady(any());
    }

    /**
     * Property 5d: Each notification method dispatches to the order's customerPhone
     * (verified via the order passed to the channel method).
     */
    @Property
    void notificationIsSentToOrderCustomerPhone(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        NotificationService service = createServiceWithMocks(channel, logRepo);

        when(channel.sendOrderConfirmed(any(CustomerOrder.class)))
                .thenReturn(UUID.randomUUID().toString());

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderConfirmed(order);

        // Verify the order passed to the channel has the correct phone
        verify(channel).sendOrderConfirmed(argThat(o ->
                phone.equals(o.getCustomerPhone())
        ));
    }

    /**
     * Property 5e: A notification log entry is saved with the correct destination phone.
     */
    @Property
    void notificationLogRecordsCorrectDestination(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber) {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        NotificationService service = createServiceWithMocks(channel, logRepo);

        when(channel.sendOrderConfirmed(any(CustomerOrder.class)))
                .thenReturn(UUID.randomUUID().toString());

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        service.notifyOrderConfirmed(order);

        // Verify a log entry was saved with the correct destination
        verify(logRepo, atLeastOnce()).save(argThat(log ->
                phone.equals(log.getDestination())
        ));
    }
}
