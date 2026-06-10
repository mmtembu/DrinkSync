package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.notification.NotificationChannel;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for delivery status webhook updates.
 *
 * Property 10: Delivery status webhook updates log entry
 * For any delivery status callback with a known providerMessageId and a valid status value
 * (sent, delivered, read, failed), the corresponding notification log entry's status SHALL
 * be updated to match the reported delivery status.
 *
 * **Validates: Requirements 5.5, 8.1, 8.2**
 */
class DeliveryStatusWebhookProperties {

    @Provide
    Arbitrary<String> providerMessageIds() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(40)
                .map(s -> "wamid." + s);
    }

    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("sent", "delivered", "read", "failed");
    }

    private NotificationLog createExistingLogEntry(String providerMessageId) {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);

        NotificationLog log = new NotificationLog();
        log.setId(1L);
        log.setOrder(order);
        log.setChannel("whatsapp");
        log.setMessageType("order_confirmed");
        log.setDestination("+27123456789");
        log.setProviderMessageId(providerMessageId);
        log.setStatus("sent");
        log.setSentAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        return log;
    }

    /**
     * Property 10a: Delivery status webhook updates log entry status to match reported status.
     */
    @Property(tries = 100)
    void deliveryStatusUpdatesLogEntryToMatchReportedStatus(
            @ForAll("providerMessageIds") String providerMessageId,
            @ForAll("validStatuses") String status) {

        NotificationLog existingLog = createExistingLogEntry(providerMessageId);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByProviderMessageId(providerMessageId))
                .thenReturn(Optional.of(existingLog));
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.empty(), orderRepo);

        service.processDeliveryStatus(providerMessageId, status);

        // Verify the log entry status was updated to match the reported status
        assertThat(existingLog.getStatus()).isEqualTo(status);
        verify(logRepo).save(existingLog);
    }

    /**
     * Property 10b: Delivery status webhook with known message ID always triggers a save.
     */
    @Property(tries = 100)
    void deliveryStatusWithKnownIdAlwaysSaves(
            @ForAll("providerMessageIds") String providerMessageId,
            @ForAll("validStatuses") String status) {

        NotificationLog existingLog = createExistingLogEntry(providerMessageId);

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByProviderMessageId(providerMessageId))
                .thenReturn(Optional.of(existingLog));
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.empty(), orderRepo);

        service.processDeliveryStatus(providerMessageId, status);

        verify(logRepo).save(any(NotificationLog.class));
    }

    /**
     * Property 10c: Delivery status webhook with unknown message ID does not save anything.
     */
    @Property(tries = 100)
    void deliveryStatusWithUnknownIdDoesNotSave(
            @ForAll("providerMessageIds") String providerMessageId,
            @ForAll("validStatuses") String status) {

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByProviderMessageId(providerMessageId))
                .thenReturn(Optional.empty());

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.empty(), orderRepo);

        service.processDeliveryStatus(providerMessageId, status);

        verify(logRepo, never()).save(any(NotificationLog.class));
    }

    /**
     * Property 10d: Each valid status value correctly updates the log entry.
     */
    @Property(tries = 100)
    void eachValidStatusValueCorrectlyUpdatesLogEntry(
            @ForAll("providerMessageIds") String providerMessageId,
            @ForAll("validStatuses") String newStatus) {

        NotificationLog existingLog = createExistingLogEntry(providerMessageId);
        String originalStatus = existingLog.getStatus();

        NotificationLogRepository logRepo = mock(NotificationLogRepository.class);
        when(logRepo.findByProviderMessageId(providerMessageId))
                .thenReturn(Optional.of(existingLog));
        when(logRepo.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRepository orderRepo = mock(OrderRepository.class);
        NotificationService service = new NotificationService(logRepo, Optional.empty(), orderRepo);

        service.processDeliveryStatus(providerMessageId, newStatus);

        // The status should now be the new status, not the original
        assertThat(existingLog.getStatus()).isEqualTo(newStatus);
    }
}
