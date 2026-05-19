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
 * Property-based tests for log status reflecting API outcome.
 *
 * Property 9: Log status reflects API outcome
 * For any successful Meta Cloud API response, the corresponding notification log entry SHALL
 * have status "sent" and a non-null providerMessageId. For any error response, the log entry
 * SHALL have status "failed" and a non-null errorMessage.
 *
 * **Validates: Requirements 5.3, 5.4**
 */
class LogStatusReflectsOutcomeProperties {

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
    Arbitrary<String> messageIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(40)
                .map(s -> "wamid." + s);
    }

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "API error: Bad Request",
                "Network timeout",
                "Connection refused",
                "Internal Server Error",
                "Rate limit exceeded"
        );
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
     * Property 9a: On success, log status is "sent" with non-null providerMessageId.
     */
    @Property(tries = 100)
    void successfulResponseSetsStatusToSentWithMessageId(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("messageIds") String messageId) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenReturn(messageId);

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

        service.notifyOrderConfirmed(order);

        // Find the final save (after send completes)
        assertThat(savedLogs).hasSizeGreaterThanOrEqualTo(2);
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo("sent");
        assertThat(finalLog.getProviderMessageId()).isNotNull();
        assertThat(finalLog.getProviderMessageId()).isEqualTo(messageId);
    }

    /**
     * Property 9b: On error, log status is "failed" with non-null errorMessage.
     */
    @Property(tries = 100)
    void errorResponseSetsStatusToFailedWithErrorMessage(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("errorMessages") String errorMsg) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderConfirmed(any())).thenThrow(new RuntimeException(errorMsg));

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

        service.notifyOrderConfirmed(order);

        // Find the final save (after failure handling)
        assertThat(savedLogs).hasSizeGreaterThanOrEqualTo(2);
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo("failed");
        assertThat(finalLog.getErrorMessage()).isNotNull();
        assertThat(finalLog.getErrorMessage()).isEqualTo(errorMsg);
    }

    /**
     * Property 9c: On success for notifyOrderReady, log status is "sent" with non-null providerMessageId.
     */
    @Property(tries = 100)
    void successfulReadyNotificationSetsStatusToSent(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("messageIds") String messageId) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendOrderReady(any())).thenReturn(messageId);

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

        service.notifyOrderReady(order);

        assertThat(savedLogs).hasSizeGreaterThanOrEqualTo(2);
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo("sent");
        assertThat(finalLog.getProviderMessageId()).isNotNull();
        assertThat(finalLog.getProviderMessageId()).isEqualTo(messageId);
    }

    /**
     * Property 9d: On error for notifyOrderCollected, log status is "failed" with non-null errorMessage.
     */
    @Property(tries = 100)
    void errorOnCollectedSetsStatusToFailed(
            @ForAll("orderIds") Long orderId,
            @ForAll("phoneNumbers") String phone,
            @ForAll("visualOrderNumbers") String visualOrderNumber,
            @ForAll("errorMessages") String errorMsg) {

        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.isEnabled()).thenReturn(true);
        when(channel.sendReceipt(any())).thenThrow(new RuntimeException(errorMsg));

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

        service.notifyOrderCollected(order);

        assertThat(savedLogs).hasSizeGreaterThanOrEqualTo(2);
        NotificationLog finalLog = savedLogs.get(savedLogs.size() - 1);
        assertThat(finalLog.getStatus()).isEqualTo("failed");
        assertThat(finalLog.getErrorMessage()).isNotNull();
        assertThat(finalLog.getErrorMessage()).isEqualTo(errorMsg);
    }
}
