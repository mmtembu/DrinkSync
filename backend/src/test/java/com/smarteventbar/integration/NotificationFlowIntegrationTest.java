package com.smarteventbar.integration;

import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the end-to-end notification flow:
 * Order state transition → NotificationService triggered → notification_log entry created.
 * <p>
 * Uses the "test" profile which configures Testcontainers JDBC URL for PostgreSQL.
 * The MockWhatsAppClient is active (access-token="mock" in test config).
 * <p>
 * Note: @Transactional is intentionally omitted because @Async methods run in a separate
 * thread/transaction. Tests use manual cleanup or rely on Testcontainers isolation.
 * <p>
 * Validates: Requirements 2.1, 2.3, 12.1
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CustomerSessionRepository customerSessionRepository;

    @Autowired
    private SpiritItemRepository spiritItemRepository;

    @Autowired
    private MixerItemRepository mixerItemRepository;

    @Autowired
    private PremadeItemRepository premadeItemRepository;

    @Autowired
    @Qualifier("notificationExecutor")
    private Executor notificationExecutor;

    private Station station;
    private CustomerSession session;
    private SpiritItem spirit;
    private MixerItem mixer;

    @BeforeEach
    void setUp() {
        // Clean up from previous runs — order matters for FK constraints
        notificationLogRepository.deleteAll();
        orderRepository.deleteAll();
        premadeItemRepository.deleteAll();
        mixerItemRepository.deleteAll();
        spiritItemRepository.deleteAll();
        customerSessionRepository.deleteAll();
        stationRepository.deleteAll();

        station = stationRepository.save(
                new Station("Notification Test Bar", "Stage Area", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        spirit = spiritItemRepository.save(
                new SpiritItem(station, "Gin", new BigDecimal("35.00"), true));

        mixer = mixerItemRepository.save(
                new MixerItem(station, "Tonic", new BigDecimal("10.00"), true));

        session = sessionService.createSession(station.getId());
    }

    /**
     * Waits for the notification executor to complete all submitted tasks.
     */
    private void awaitNotificationCompletion() throws InterruptedException {
        if (notificationExecutor instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor) {
            long deadline = System.currentTimeMillis() + 10_000;
            while (taskExecutor.getThreadPoolExecutor().getActiveCount() > 0
                    || !taskExecutor.getThreadPoolExecutor().getQueue().isEmpty()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new RuntimeException("Timed out waiting for notification executor");
                }
                Thread.sleep(50);
            }
            // Allow DB writes to flush
            Thread.sleep(200);
        }
    }

    // -----------------------------------------------------------------------
    // Test: NotificationService creates log entry for opted-in order
    // -----------------------------------------------------------------------

    @Test
    void notifyOrderConfirmed_createsNotificationLogEntry() throws InterruptedException {
        // Create an order with WhatsApp opt-in
        CustomerOrder order = createOptedInOrder();

        // Directly invoke the notification service (simulating what happens after state transition)
        notificationService.notifyOrderConfirmed(order);
        awaitNotificationCompletion();

        // Verify notification log entry was created
        List<NotificationLog> logs = notificationLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Notification log entry should be created");

        NotificationLog logEntry = logs.stream()
                .filter(l -> l.getOrder().getId().equals(order.getId()))
                .filter(l -> "order_confirmed".equals(l.getMessageType()))
                .findFirst()
                .orElse(null);

        assertNotNull(logEntry, "Should have an order_confirmed log entry");
        assertEquals("whatsapp", logEntry.getChannel());
        assertEquals("+27821234567", logEntry.getDestination());
        assertEquals("sent", logEntry.getStatus());
        assertNotNull(logEntry.getProviderMessageId(), "Mock client should provide a message ID");
        assertNotNull(logEntry.getSentAt());
    }

    @Test
    void notifyOrderReady_createsNotificationLogEntry() throws InterruptedException {
        CustomerOrder order = createOptedInOrder();
        order.setState(OrderState.READY);
        final CustomerOrder savedOrder = orderRepository.save(order);

        notificationService.notifyOrderReady(savedOrder);
        awaitNotificationCompletion();

        NotificationLog logEntry = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(savedOrder.getId()))
                .filter(l -> "order_ready".equals(l.getMessageType()))
                .findFirst()
                .orElse(null);

        assertNotNull(logEntry, "Should have an order_ready log entry");
        assertEquals("whatsapp", logEntry.getChannel());
        assertEquals("sent", logEntry.getStatus());
        assertNotNull(logEntry.getProviderMessageId());
    }

    @Test
    void notifyOrderCollected_createsNotificationLogEntry() throws InterruptedException {
        CustomerOrder order = createOptedInOrder();
        order.setState(OrderState.COLLECTED);
        final CustomerOrder savedOrder = orderRepository.save(order);

        notificationService.notifyOrderCollected(savedOrder);
        awaitNotificationCompletion();

        NotificationLog logEntry = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(savedOrder.getId()))
                .filter(l -> "order_receipt".equals(l.getMessageType()))
                .findFirst()
                .orElse(null);

        assertNotNull(logEntry, "Should have an order_receipt log entry");
        assertEquals("whatsapp", logEntry.getChannel());
        assertEquals("sent", logEntry.getStatus());
    }

    // -----------------------------------------------------------------------
    // Test: No notification for non-opted-in orders
    // -----------------------------------------------------------------------

    @Test
    void notifyOrderConfirmed_skipsNonOptedInOrder() throws InterruptedException {
        CustomerOrder order = createNonOptedInOrder();

        notificationService.notifyOrderConfirmed(order);
        awaitNotificationCompletion();

        List<NotificationLog> logs = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(order.getId()))
                .toList();

        assertTrue(logs.isEmpty(), "No notification log should be created for non-opted-in order");
    }

    // -----------------------------------------------------------------------
    // Test: Deduplication prevents duplicate notifications
    // -----------------------------------------------------------------------

    @Test
    void notifyOrderConfirmed_deduplicatesPreviouslySentNotification() throws InterruptedException {
        CustomerOrder order = createOptedInOrder();

        // First notification — should succeed
        notificationService.notifyOrderConfirmed(order);
        awaitNotificationCompletion();

        long countAfterFirst = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(order.getId()))
                .filter(l -> "order_confirmed".equals(l.getMessageType()))
                .count();
        assertEquals(1, countAfterFirst);

        // Second notification — should be deduplicated (no new entry)
        notificationService.notifyOrderConfirmed(order);
        awaitNotificationCompletion();

        long countAfterSecond = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(order.getId()))
                .filter(l -> "order_confirmed".equals(l.getMessageType()))
                .count();
        assertEquals(1, countAfterSecond, "Duplicate notification should be skipped");
    }

    // -----------------------------------------------------------------------
    // Test: Async execution does not block order transition
    // -----------------------------------------------------------------------

    @Test
    void orderStateTransition_completesWithoutWaitingForNotification() {
        // This test verifies that the order state transition method returns quickly
        // even when notifications are triggered. Since the test profile uses the
        // MockWhatsAppClient (which is fast), we verify the transition completes
        // within a reasonable time.

        // Create order and progress to AWAITING_PAYMENT
        com.smarteventbar.dto.OrderItemRequest itemReq = new com.smarteventbar.dto.OrderItemRequest();
        itemReq.setItemType(com.smarteventbar.model.enums.OrderItemType.CUSTOM_DRINK);
        itemReq.setSpiritItemIds(List.of(spirit.getId()));
        itemReq.setMixerItemIds(List.of(mixer.getId()));
        itemReq.setCupOption(com.smarteventbar.model.enums.CupOption.NEW_CUP);
        itemReq.setQuantity(1);

        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(itemReq));

        // Checkout with phone and opt-in
        order = orderService.checkout(order.getId(), session.getSessionId(),
                "+27821234567", true);

        // Measure time for payment confirmation (which triggers notification)
        long startTime = System.currentTimeMillis();
        order = orderService.confirmPayment(order.getId(), session.getSessionId(), UUID.randomUUID());
        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals(OrderState.PAID, order.getState());
        // The transition should complete quickly (< 5 seconds) even with notification dispatch
        assertTrue(elapsed < 5000,
                "Order state transition should complete quickly, took " + elapsed + "ms");
    }

    // -----------------------------------------------------------------------
    // Test: Delivery status processing updates log entry
    // -----------------------------------------------------------------------

    @Test
    void processDeliveryStatus_updatesNotificationLogStatus() throws InterruptedException {
        CustomerOrder order = createOptedInOrder();
        notificationService.notifyOrderConfirmed(order);
        awaitNotificationCompletion();

        // Find the log entry and get its provider message ID
        NotificationLog logEntry = notificationLogRepository.findAll().stream()
                .filter(l -> l.getOrder().getId().equals(order.getId()))
                .filter(l -> "order_confirmed".equals(l.getMessageType()))
                .findFirst()
                .orElseThrow();

        String providerMessageId = logEntry.getProviderMessageId();
        assertNotNull(providerMessageId);

        // Process delivery status update
        notificationService.processDeliveryStatus(providerMessageId, "delivered");

        // Verify the log entry was updated
        NotificationLog updated = notificationLogRepository.findById(logEntry.getId()).orElseThrow();
        assertEquals("delivered", updated.getStatus());
    }

    @Test
    void processDeliveryStatus_handlesUnknownMessageIdGracefully() {
        // Should not throw — just logs a warning
        assertDoesNotThrow(() ->
                notificationService.processDeliveryStatus("wamid.unknown", "delivered"));
    }

    // -----------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------

    private CustomerOrder createOptedInOrder() {
        CustomerOrder order = new CustomerOrder(station, session);
        order.setCustomerPhone("+27821234567");
        order.setWhatsappOptIn(true);
        order.setState(OrderState.PAID);
        order.setVisualOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 4));
        return orderRepository.save(order);
    }

    private CustomerOrder createNonOptedInOrder() {
        CustomerOrder order = new CustomerOrder(station, session);
        order.setCustomerPhone("+27821234567");
        order.setWhatsappOptIn(false);
        order.setState(OrderState.PAID);
        order.setVisualOrderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 4));
        return orderRepository.save(order);
    }
}
