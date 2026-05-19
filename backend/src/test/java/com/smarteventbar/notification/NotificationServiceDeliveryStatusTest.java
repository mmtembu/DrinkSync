package com.smarteventbar.notification;

import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - processDeliveryStatus")
class NotificationServiceDeliveryStatusTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationChannel whatsAppChannel;

    @Mock
    private OrderRepository orderRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationLogRepository, Optional.of(whatsAppChannel), orderRepository);
    }

    @Test
    @DisplayName("Should update status to 'delivered' for known provider message ID")
    void processDeliveryStatus_withKnownMessageId_updatesStatusToDelivered() {
        NotificationLog logEntry = createLogEntry("wamid.abc123", "sent");
        when(notificationLogRepository.findByProviderMessageId("wamid.abc123"))
                .thenReturn(Optional.of(logEntry));

        notificationService.processDeliveryStatus("wamid.abc123", "delivered");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("delivered", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should update status to 'read' for known provider message ID")
    void processDeliveryStatus_withKnownMessageId_updatesStatusToRead() {
        NotificationLog logEntry = createLogEntry("wamid.xyz789", "delivered");
        when(notificationLogRepository.findByProviderMessageId("wamid.xyz789"))
                .thenReturn(Optional.of(logEntry));

        notificationService.processDeliveryStatus("wamid.xyz789", "read");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("read", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should update status to 'failed' for known provider message ID")
    void processDeliveryStatus_withKnownMessageId_updatesStatusToFailed() {
        NotificationLog logEntry = createLogEntry("wamid.fail001", "sent");
        when(notificationLogRepository.findByProviderMessageId("wamid.fail001"))
                .thenReturn(Optional.of(logEntry));

        notificationService.processDeliveryStatus("wamid.fail001", "failed");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should update status to 'sent' for known provider message ID")
    void processDeliveryStatus_withKnownMessageId_updatesStatusToSent() {
        NotificationLog logEntry = createLogEntry("wamid.sent001", "pending");
        when(notificationLogRepository.findByProviderMessageId("wamid.sent001"))
                .thenReturn(Optional.of(logEntry));

        notificationService.processDeliveryStatus("wamid.sent001", "sent");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("sent", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("Should not save when provider message ID is unknown")
    void processDeliveryStatus_withUnknownMessageId_doesNotSave() {
        when(notificationLogRepository.findByProviderMessageId("wamid.unknown"))
                .thenReturn(Optional.empty());

        notificationService.processDeliveryStatus("wamid.unknown", "delivered");

        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should not save when delivery status is invalid")
    void processDeliveryStatus_withInvalidStatus_doesNotSave() {
        NotificationLog logEntry = createLogEntry("wamid.abc123", "sent");
        when(notificationLogRepository.findByProviderMessageId("wamid.abc123"))
                .thenReturn(Optional.of(logEntry));

        notificationService.processDeliveryStatus("wamid.abc123", "invalid_status");

        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should process delivery status even without WhatsApp channel configured")
    void processDeliveryStatus_withNoChannel_stillProcessesStatus() {
        NotificationService serviceWithoutChannel = new NotificationService(
                notificationLogRepository, Optional.empty(), orderRepository);

        NotificationLog logEntry = createLogEntry("wamid.nochannel", "sent");
        when(notificationLogRepository.findByProviderMessageId("wamid.nochannel"))
                .thenReturn(Optional.of(logEntry));

        serviceWithoutChannel.processDeliveryStatus("wamid.nochannel", "delivered");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertEquals("delivered", captor.getValue().getStatus());
    }

    private NotificationLog createLogEntry(String providerMessageId, String status) {
        NotificationLog logEntry = new NotificationLog();
        logEntry.setId(1L);
        logEntry.setProviderMessageId(providerMessageId);
        logEntry.setStatus(status);
        logEntry.setChannel("whatsapp");
        logEntry.setMessageType("order_confirmed");
        logEntry.setDestination("+27821234567");
        logEntry.setSentAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        return logEntry;
    }
}
