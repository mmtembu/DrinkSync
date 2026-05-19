package com.smarteventbar.notification.properties;

import com.smarteventbar.exception.WhatsAppApiException;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for notification failure resilience.
 *
 * Property 7: Notification failure does not affect order state
 * For any order where the WhatsApp client throws an exception (network timeout, API error,
 * or any other failure), the order's state SHALL remain at the target state of the transition,
 * and a notification log entry with status "failed" and a non-null error message SHALL exist.
 *
 * **Validates: Requirements 2.4, 3.4, 4.4, 12.2, 12.3**
 */
class NotificationFailureResilienceProperties {

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

    @Provide
    Arbitrary<RuntimeException> exceptions() {
        return Arbitraries.of(
                new WhatsAppApiException("API error: Bad Request", 400, "INVALID_PARAMETER"),
                new WhatsAppApiException("API error: Internal Server Error", 500, "INTERNAL_ERROR"),
                new WhatsAppApiException("API error: Unauthorized", 401, "AUTH_ERROR"),
                new RuntimeException("Network timeout"),
                new RuntimeException("Connection refused"),
                new RuntimeException("DNS resolution failed"),
                new IllegalStateException("Unexpected state")
        );
    }

    private CustomerOrder createOptedInOrder(Long id, String phone, String visualOrderNumber, OrderState state) {
        Station station = new Station();
        station.setName("Test Station");

        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(true);
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(49.99));
        order.setState(state);
        order.setStation(station);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /**
     * Property 7a: When channel throws on notifyOrderConfirmed, order state remains unchanged
     * and log entry has status "failed" with non-null error message.
     */
    @Property(tries = 100)
    void failureOnConfirmedDoesNotAffectOrderState(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("exceptions") RuntimeException exception) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any(CustomerOrder.class))).thenThrow(exception);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber, OrderState.PAID);
        OrderState stateBefore = order.getState();

        service.notifyOrderConfirmed(order);

        // Order state is unchanged
        assertThat(order.getState()).isEqualTo(stateBefore);

        // Verify log entry saved with "failed" status and non-null error message
        verify(logRepo, atLeast(2)).save(argThat(log ->
                "failed".equals(log.getStatus()) && log.getErrorMessage() != null
        ));
    }

    /**
     * Property 7b: When channel throws on notifyOrderReady, order state remains unchanged
     * and log entry has status "failed" with non-null error message.
     */
    @Property(tries = 100)
    void failureOnReadyDoesNotAffectOrderState(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("exceptions") RuntimeException exception) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderReady(any(CustomerOrder.class))).thenThrow(exception);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber, OrderState.READY);
        OrderState stateBefore = order.getState();

        service.notifyOrderReady(order);

        assertThat(order.getState()).isEqualTo(stateBefore);

        verify(logRepo, atLeast(2)).save(argThat(log ->
                "failed".equals(log.getStatus()) && log.getErrorMessage() != null
        ));
    }

    /**
     * Property 7c: When channel throws on notifyOrderCollected, order state remains unchanged
     * and log entry has status "failed" with non-null error message.
     */
    @Property(tries = 100)
    void failureOnCollectedDoesNotAffectOrderState(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("exceptions") RuntimeException exception) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendReceipt(any(CustomerOrder.class))).thenThrow(exception);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber, OrderState.COLLECTED);
        OrderState stateBefore = order.getState();

        service.notifyOrderCollected(order);

        assertThat(order.getState()).isEqualTo(stateBefore);

        verify(logRepo, atLeast(2)).save(argThat(log ->
                "failed".equals(log.getStatus()) && log.getErrorMessage() != null
        ));
    }

    /**
     * Property 7d: Regardless of exception type, the notification failure never propagates
     * (no exception thrown from the notification method).
     */
    @Property(tries = 100)
    void failureNeverPropagatesFromNotificationMethod(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("exceptions") RuntimeException exception,
            @ForAll @IntRange(min = 0, max = 2) int methodIndex) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenThrow(exception);
        when(channel.sendOrderReady(any())).thenThrow(exception);
        when(channel.sendReceipt(any())).thenThrow(exception);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber, OrderState.PAID);

        // Should not throw — failure is caught internally
        switch (methodIndex) {
            case 0 -> service.notifyOrderConfirmed(order);
            case 1 -> service.notifyOrderReady(order);
            case 2 -> service.notifyOrderCollected(order);
        }

        // If we reach here, no exception propagated — test passes
        assertThat(order.getState()).isNotNull();
    }
}
