package com.smarteventbar.notification.properties;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for notification logging completeness.
 *
 * Property 8: Every notification attempt produces a log entry
 * For any outbound notification attempt (regardless of success or failure), a notification_log
 * entry SHALL exist containing the order ID, channel ("whatsapp"), message type, destination
 * phone number, a non-null status, and a non-null timestamp.
 *
 * **Validates: Requirements 5.1, 5.2**
 */
class NotificationLoggingCompletenessProperties {

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
        Station station = new Station();
        station.setName("Test Station");

        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(true);
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(49.99));
        order.setState(OrderState.PAID);
        order.setStation(station);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /**
     * Property 8a: Successful notification attempt produces a log entry with all required fields.
     */
    @Property(tries = 100)
    void successfulNotificationProducesLogEntry(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll @IntRange(min = 0, max = 2) int methodIndex) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenReturn(UUID.randomUUID().toString());
        when(channel.sendOrderReady(any())).thenReturn(UUID.randomUUID().toString());
        when(channel.sendReceipt(any())).thenReturn(UUID.randomUUID().toString());

        List<NotificationLog> savedLogs = new ArrayList<>();
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            if (log.getSentAt() == null) log.setSentAt(LocalDateTime.now());
            if (log.getUpdatedAt() == null) log.setUpdatedAt(LocalDateTime.now());
            savedLogs.add(log);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        switch (methodIndex) {
            case 0 -> service.notifyOrderConfirmed(order);
            case 1 -> service.notifyOrderReady(order);
            case 2 -> service.notifyOrderCollected(order);
        }

        // Verify at least one log entry was saved
        assertThat(savedLogs).isNotEmpty();

        // Verify the final log entry has all required fields
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getOrder()).isNotNull();
        assertThat(finalLog.getOrder().getId()).isEqualTo(orderId);
        assertThat(finalLog.getChannel()).isEqualTo("whatsapp");
        assertThat(finalLog.getMessageType()).isNotNull();
        assertThat(finalLog.getDestination()).isEqualTo(phone);
        assertThat(finalLog.getStatus()).isNotNull();
    }

    /**
     * Property 8b: Failed notification attempt also produces a log entry with all required fields.
     */
    @Property(tries = 100)
    void failedNotificationProducesLogEntry(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll @IntRange(min = 0, max = 2) int methodIndex) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenThrow(new RuntimeException("API failure"));
        when(channel.sendOrderReady(any())).thenThrow(new RuntimeException("Network timeout"));
        when(channel.sendReceipt(any())).thenThrow(new RuntimeException("Connection refused"));

        List<NotificationLog> savedLogs = new ArrayList<>();
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            if (log.getSentAt() == null) log.setSentAt(LocalDateTime.now());
            if (log.getUpdatedAt() == null) log.setUpdatedAt(LocalDateTime.now());
            savedLogs.add(log);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        switch (methodIndex) {
            case 0 -> service.notifyOrderConfirmed(order);
            case 1 -> service.notifyOrderReady(order);
            case 2 -> service.notifyOrderCollected(order);
        }

        // Verify at least one log entry was saved
        assertThat(savedLogs).isNotEmpty();

        // Verify the final log entry has all required fields
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getOrder()).isNotNull();
        assertThat(finalLog.getOrder().getId()).isEqualTo(orderId);
        assertThat(finalLog.getChannel()).isEqualTo("whatsapp");
        assertThat(finalLog.getMessageType()).isNotNull();
        assertThat(finalLog.getDestination()).isEqualTo(phone);
        assertThat(finalLog.getStatus()).isNotNull();
    }

    /**
     * Property 8c: The message type in the log entry matches the notification method called.
     */
    @Property(tries = 100)
    void logEntryMessageTypeMatchesNotificationMethod(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll @IntRange(min = 0, max = 2) int methodIndex) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenReturn(UUID.randomUUID().toString());
        when(channel.sendOrderReady(any())).thenReturn(UUID.randomUUID().toString());
        when(channel.sendReceipt(any())).thenReturn(UUID.randomUUID().toString());

        List<NotificationLog> savedLogs = new ArrayList<>();
        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByOrderIdAndMessageTypeAndStatusIn(anyLong(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) log.setId(1L);
            savedLogs.add(log);
            return log;
        });

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.of(channel), orderRepo);

        CustomerOrder order = createOptedInOrder(orderId, phone, visualOrderNumber);

        String expectedMessageType;
        switch (methodIndex) {
            case 0 -> {
                service.notifyOrderConfirmed(order);
                expectedMessageType = "order_confirmed";
            }
            case 1 -> {
                service.notifyOrderReady(order);
                expectedMessageType = "order_ready";
            }
            default -> {
                service.notifyOrderCollected(order);
                expectedMessageType = "order_receipt";
            }
        }

        // Verify the log entry has the correct message type
        assertThat(savedLogs).isNotEmpty();
        assertThat(savedLogs.get(0).getMessageType()).isEqualTo(expectedMessageType);
    }
}
