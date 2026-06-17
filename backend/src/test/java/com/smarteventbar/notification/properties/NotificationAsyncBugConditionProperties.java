package com.smarteventbar.notification.properties;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.CustomerSessionRepository;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug Condition Exploration Test — Property 1: Async Notification Methods Fail in @Transactional Tests
 *
 * This test demonstrates the bug where @Async("notificationExecutor") causes notification
 * methods to execute on a separate thread that:
 * (a) Cannot see uncommitted test data due to transaction isolation
 * (b) Completes after test assertions have already run (race condition)
 *
 * The test creates opted-in CustomerOrders with random phone numbers and order numbers,
 * calls notificationService.notifyOrderConfirmed(order), then asserts that a notification
 * log entry exists with status "sent".
 *
 * EXPECTED OUTCOME ON UNFIXED CODE: This test FAILS because the async thread hasn't
 * persisted the notification log entry / can't see the test transaction data when
 * assertions run.
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationAsyncBugConditionProperties {

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

    private Station station;
    private CustomerSession session;
    private final Random random = new Random(42);

    @BeforeEach
    void setUp() {
        station = stationRepository.save(
                new Station("Bug Condition Test Bar", "Test Area", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        session = new CustomerSession(UUID.randomUUID().toString(), station);
        session = customerSessionRepository.save(session);
    }

    /**
     * Property 1: Bug Condition — For any opted-in CustomerOrder with a valid phone number,
     * calling notifyOrderConfirmed within a @Transactional test context should result in
     * a notification log entry with status "sent" being immediately visible.
     *
     * This test uses multiple random inputs (lightweight property check) to confirm
     * the bug exists regardless of input data.
     *
     * **Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5**
     */
    @Test
    void asyncNotificationMethodFailsInTransactionalTest_bugCondition() {
        // Generate and test multiple random orders to act as a property-based check
        int sampleCount = 10;
        int failureCount = 0;

        for (int i = 0; i < sampleCount; i++) {
            String phone = generateRandomPhone();
            String orderNumber = "ORD-" + generateRandomAlphanumeric(6);

            CustomerOrder order = createOptedInOrder(phone, orderNumber);

            // Clear any previous log entries for clean assertion
            long logCountBefore = notificationLogRepository.findAll().stream()
                    .filter(l -> l.getOrder().getId().equals(order.getId()))
                    .count();
            assertEquals(0, logCountBefore, "No log entries should exist before notification");

            // Call the async notification method
            notificationService.notifyOrderConfirmed(order);

            // Assert that a log entry with status "sent" exists for this order
            // On UNFIXED code, this will fail because the async thread hasn't persisted the entry
            List<NotificationLog> logs = notificationLogRepository.findAll().stream()
                    .filter(l -> l.getOrder().getId().equals(order.getId()))
                    .filter(l -> "order_confirmed".equals(l.getMessageType()))
                    .filter(l -> "sent".equals(l.getStatus()))
                    .toList();

            if (logs.isEmpty()) {
                failureCount++;
            }
        }

        // The property: ALL invocations should have a "sent" log entry visible immediately
        assertEquals(0, failureCount,
                "Bug condition confirmed: " + failureCount + "/" + sampleCount +
                " notification invocations produced no visible 'sent' log entry. " +
                "The @Async proxy dispatches to a separate thread, so the log entry is " +
                "either not persisted yet (race condition) or not visible to this transaction " +
                "(transaction isolation). This confirms the bug exists.");
    }

    // -----------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------

    private CustomerOrder createOptedInOrder(String phone, String visualOrderNumber) {
        CustomerOrder order = new CustomerOrder(station, session);
        order.setCustomerPhone(phone);
        order.setWhatsappOptIn(true);
        order.setState(OrderState.PAID);
        order.setVisualOrderNumber(visualOrderNumber);
        order.setTotalPrice(BigDecimal.valueOf(random.nextInt(100) + 10));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    private String generateRandomPhone() {
        // Generate a random South African phone number format: +27 8X XXX XXXX
        int secondDigit = random.nextInt(3) + 6; // 6, 7, or 8
        StringBuilder sb = new StringBuilder("+27");
        sb.append(secondDigit);
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String generateRandomAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
