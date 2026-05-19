package com.smarteventbar.notification;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService core dispatch logic:
 * - Async dispatch invocation
 * - Deduplication skip
 * - Disabled channel skip
 * - Opt-in false → no send
 * - Failure handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService - Core Dispatch Logic")
class NotificationServiceTest {

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

    @Nested
    @DisplayName("Async dispatch invocation")
    class AsyncDispatchTests {

        @Test
        @DisplayName("notifyOrderConfirmed delegates to channel.sendOrderConfirmed when opt-in and enabled")
        void notifyOrderConfirmed_delegatesToChannel() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderConfirmed(order)).thenReturn("wamid.123");

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel).sendOrderConfirmed(order);
        }

        @Test
        @DisplayName("notifyOrderReady delegates to channel.sendOrderReady when opt-in and enabled")
        void notifyOrderReady_delegatesToChannel() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_ready"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderReady(order)).thenReturn("wamid.456");

            notificationService.notifyOrderReady(order);

            verify(whatsAppChannel).sendOrderReady(order);
        }

        @Test
        @DisplayName("notifyOrderCollected delegates to channel.sendReceipt when opt-in and enabled")
        void notifyOrderCollected_delegatesToChannel() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_receipt"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendReceipt(order)).thenReturn("wamid.789");

            notificationService.notifyOrderCollected(order);

            verify(whatsAppChannel).sendReceipt(order);
        }

        @Test
        @DisplayName("Creates PENDING log entry before sending and updates to SENT on success")
        void createsLogEntryAndUpdatesToSent() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());

            // Capture status at each save invocation since the same object is mutated
            java.util.List<String> statusesAtSave = new java.util.ArrayList<>();
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> {
                        NotificationLog log = inv.getArgument(0);
                        statusesAtSave.add(log.getStatus());
                        return log;
                    });
            when(whatsAppChannel.sendOrderConfirmed(order)).thenReturn("wamid.abc");

            notificationService.notifyOrderConfirmed(order);

            assertEquals(2, statusesAtSave.size());
            // First save: PENDING
            assertEquals("pending", statusesAtSave.get(0));
            // Second save: SENT
            assertEquals("sent", statusesAtSave.get(1));

            // Verify final state has provider message ID
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(notificationLogRepository, times(2)).save(captor.capture());
            NotificationLog finalLog = captor.getAllValues().get(1);
            assertEquals("wamid.abc", finalLog.getProviderMessageId());
            assertEquals("whatsapp", finalLog.getChannel());
            assertEquals("order_confirmed", finalLog.getMessageType());
            assertEquals("+27821234567", finalLog.getDestination());
        }
    }

    @Nested
    @DisplayName("Deduplication skip")
    class DeduplicationTests {

        @Test
        @DisplayName("Skips sending when existing SENT entry found for same order and message type")
        void skipsWhenExistingSentEntry() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);

            NotificationLog existingLog = new NotificationLog();
            existingLog.setStatus("sent");
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.of(existingLog));

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel, never()).sendOrderConfirmed(any());
            verify(notificationLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Skips sending when existing DELIVERED entry found")
        void skipsWhenExistingDeliveredEntry() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);

            NotificationLog existingLog = new NotificationLog();
            existingLog.setStatus("delivered");
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.of(existingLog));

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel, never()).sendOrderConfirmed(any());
        }

        @Test
        @DisplayName("Skips sending when existing READ entry found")
        void skipsWhenExistingReadEntry() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);

            NotificationLog existingLog = new NotificationLog();
            existingLog.setStatus("read");
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_ready"), anyList())).thenReturn(Optional.of(existingLog));

            notificationService.notifyOrderReady(order);

            verify(whatsAppChannel, never()).sendOrderReady(any());
        }

        @Test
        @DisplayName("Allows sending when no existing entry found (dedup check returns empty)")
        void allowsSendingWhenNoDuplicate() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderConfirmed(order)).thenReturn("wamid.new");

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel).sendOrderConfirmed(order);
        }
    }

    @Nested
    @DisplayName("Disabled channel skip")
    class DisabledChannelTests {

        @Test
        @DisplayName("Skips sending when channel.isEnabled() returns false")
        void skipsWhenChannelDisabled() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(false);

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel, never()).sendOrderConfirmed(any());
            verify(notificationLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Skips sending when channel is absent (Optional.empty)")
        void skipsWhenChannelAbsent() {
            NotificationService serviceWithoutChannel = new NotificationService(
                    notificationLogRepository, Optional.empty(), orderRepository);

            CustomerOrder order = createOptedInOrder();

            serviceWithoutChannel.notifyOrderConfirmed(order);

            verify(notificationLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Skips all notification types when channel is disabled")
        void skipsAllTypesWhenDisabled() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(false);

            notificationService.notifyOrderConfirmed(order);
            notificationService.notifyOrderReady(order);
            notificationService.notifyOrderCollected(order);

            verify(whatsAppChannel, never()).sendOrderConfirmed(any());
            verify(whatsAppChannel, never()).sendOrderReady(any());
            verify(whatsAppChannel, never()).sendReceipt(any());
        }
    }

    @Nested
    @DisplayName("Opt-in false → no send")
    class OptInTests {

        @Test
        @DisplayName("Does not send when whatsappOptIn is false")
        void doesNotSendWhenOptInFalse() {
            CustomerOrder order = createOptedOutOrder();

            notificationService.notifyOrderConfirmed(order);

            verify(whatsAppChannel, never()).sendOrderConfirmed(any());
            verify(whatsAppChannel, never()).isEnabled();
            verify(notificationLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Does not check deduplication when opt-in is false")
        void doesNotCheckDedupWhenOptInFalse() {
            CustomerOrder order = createOptedOutOrder();

            notificationService.notifyOrderReady(order);

            verify(notificationLogRepository, never()).findByOrderIdAndMessageTypeAndStatusIn(
                    anyLong(), anyString(), anyList());
        }

        @Test
        @DisplayName("Does not send any notification type when opt-in is false")
        void doesNotSendAnyTypeWhenOptInFalse() {
            CustomerOrder order = createOptedOutOrder();

            notificationService.notifyOrderConfirmed(order);
            notificationService.notifyOrderReady(order);
            notificationService.notifyOrderCollected(order);

            verifyNoInteractions(whatsAppChannel);
            verify(notificationLogRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureHandlingTests {

        @Test
        @DisplayName("Catches exception from channel and marks log entry as FAILED with error message")
        void catchesExceptionAndMarksAsFailed() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderConfirmed(order))
                    .thenThrow(new RuntimeException("Network timeout"));

            // Should not throw
            assertDoesNotThrow(() -> notificationService.notifyOrderConfirmed(order));

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(notificationLogRepository, times(2)).save(captor.capture());

            List<NotificationLog> savedLogs = captor.getAllValues();
            // Second save should be FAILED with error message
            NotificationLog failedLog = savedLogs.get(1);
            assertEquals("failed", failedLog.getStatus());
            assertEquals("Network timeout", failedLog.getErrorMessage());
            assertNull(failedLog.getProviderMessageId());
        }

        @Test
        @DisplayName("Does not propagate exception to caller")
        void doesNotPropagateException() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderConfirmed(order))
                    .thenThrow(new RuntimeException("WhatsApp API unreachable"));

            assertDoesNotThrow(() -> notificationService.notifyOrderConfirmed(order));
        }

        @Test
        @DisplayName("Handles WhatsAppApiException and records error details")
        void handlesWhatsAppApiException() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_ready"), anyList())).thenReturn(Optional.empty());
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(whatsAppChannel.sendOrderReady(order))
                    .thenThrow(new com.smarteventbar.exception.WhatsAppApiException(
                            "Invalid phone number", 400, "100"));

            assertDoesNotThrow(() -> notificationService.notifyOrderReady(order));

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(notificationLogRepository, times(2)).save(captor.capture());

            NotificationLog failedLog = captor.getAllValues().get(1);
            assertEquals("failed", failedLog.getStatus());
            assertNotNull(failedLog.getErrorMessage());
            assertTrue(failedLog.getErrorMessage().contains("Invalid phone number"));
        }

        @Test
        @DisplayName("Still creates PENDING log entry even when send fails")
        void createsPendingLogBeforeFailure() {
            CustomerOrder order = createOptedInOrder();
            when(whatsAppChannel.isEnabled()).thenReturn(true);
            when(notificationLogRepository.findByOrderIdAndMessageTypeAndStatusIn(
                    eq(1L), eq("order_confirmed"), anyList())).thenReturn(Optional.empty());

            // Capture status at each save invocation since the same object is mutated
            java.util.List<String> statusesAtSave = new java.util.ArrayList<>();
            when(notificationLogRepository.save(any(NotificationLog.class)))
                    .thenAnswer(inv -> {
                        NotificationLog log = inv.getArgument(0);
                        statusesAtSave.add(log.getStatus());
                        return log;
                    });
            when(whatsAppChannel.sendOrderConfirmed(order))
                    .thenThrow(new RuntimeException("Connection refused"));

            notificationService.notifyOrderConfirmed(order);

            assertEquals(2, statusesAtSave.size());
            // First save is PENDING
            assertEquals("pending", statusesAtSave.get(0));
            // Second save is FAILED
            assertEquals("failed", statusesAtSave.get(1));
        }
    }

    // --- Helper methods ---

    private CustomerOrder createOptedInOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setVisualOrderNumber("ORD-001");
        order.setState(OrderState.PAID);
        order.setTotalPrice(BigDecimal.valueOf(150.00));
        order.setCustomerPhone("+27821234567");
        order.setWhatsappOptIn(true);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private CustomerOrder createOptedOutOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(2L);
        order.setVisualOrderNumber("ORD-002");
        order.setState(OrderState.PAID);
        order.setTotalPrice(BigDecimal.valueOf(75.00));
        order.setCustomerPhone("+27829876543");
        order.setWhatsappOptIn(false);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
