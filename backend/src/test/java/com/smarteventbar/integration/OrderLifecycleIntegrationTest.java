package com.smarteventbar.integration;

import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.model.enums.TransitionTrigger;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full order lifecycle using PostgreSQL via Testcontainers.
 * <p>
 * Uses the "test" profile which configures Testcontainers JDBC URL for PostgreSQL.
 * If Testcontainers cannot connect to Docker, use the "integration" profile with
 * a pre-started PostgreSQL instance instead.
 * <p>
 * Validates: Requirements 4.1, 4.3, 8.1, 8.2, 8.3, 10.1, 10.2, 13.1, 15.1
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderLifecycleIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private SpiritItemRepository spiritItemRepository;

    @Autowired
    private MixerItemRepository mixerItemRepository;

    @Autowired
    private PremadeItemRepository premadeItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStateHistoryRepository orderStateHistoryRepository;

    @Autowired
    private CustomerSessionRepository customerSessionRepository;

    private Station station;
    private SpiritItem spirit;
    private MixerItem mixer;
    private PremadeItem premade;
    private CustomerSession session;

    @BeforeEach
    void setUp() {
        station = stationRepository.save(
                new Station("Test Bar", "Main Stage Area", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        spirit = spiritItemRepository.save(
                new SpiritItem(station, "Vodka", new BigDecimal("30.00"), true));

        mixer = mixerItemRepository.save(
                new MixerItem(station, "Lemonade", new BigDecimal("10.00"), true));

        premade = premadeItemRepository.save(
                new PremadeItem(station, "Craft Beer", "Local IPA", new BigDecimal("45.00"), true));

        session = sessionService.createSession(station.getId());
    }

    // --- Helper methods ---

    private OrderItemRequest customDrinkRequest() {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemType(OrderItemType.CUSTOM_DRINK);
        req.setSpiritItemIds(java.util.List.of(spirit.getId()));
        req.setMixerItemIds(java.util.List.of(mixer.getId()));
        req.setCupOption(CupOption.NEW_CUP);
        req.setQuantity(1);
        return req;
    }

    private OrderItemRequest premadeRequest() {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemType(OrderItemType.PREMADE);
        req.setPremadeItemId(premade.getId());
        req.setQuantity(2);
        return req;
    }

    private void assertStateHistoryEntry(List<OrderStateHistory> history, int index,
                                         OrderState expectedFrom, OrderState expectedTo) {
        assertTrue(history.size() > index,
                "Expected at least " + (index + 1) + " history entries, got " + history.size());
        OrderStateHistory entry = history.get(index);
        assertEquals(expectedFrom, entry.getFromState());
        assertEquals(expectedTo, entry.getToState());
        assertNotNull(entry.getTransitionedAt(), "Transition timestamp must not be null");
    }

    // -----------------------------------------------------------------------
    // Test 1: Full happy path — create → checkout → pay → prepare → ready → collect
    // Validates: Req 4.1, 4.3, 8.1, 8.2, 8.3, 10.1, 10.2
    // -----------------------------------------------------------------------

    @Test
    void fullOrderLifecycle_createToCollected() {
        // 1. Create order (DRAFT)
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(customDrinkRequest(), premadeRequest()));

        assertNotNull(order.getId());
        assertEquals(OrderState.DRAFT, order.getState());
        assertNotNull(order.getTotalPrice());
        assertTrue(order.getTotalPrice().compareTo(BigDecimal.ZERO) > 0);

        Long orderId = order.getId();

        // 2. Checkout (DRAFT → AWAITING_PAYMENT) — Req 4.1
        order = orderService.checkout(orderId, session.getSessionId());
        assertEquals(OrderState.AWAITING_PAYMENT, order.getState());

        // 3. Pay (AWAITING_PAYMENT → PAID) — Req 4.3
        UUID idempotencyKey = UUID.randomUUID();
        order = orderService.confirmPayment(orderId, session.getSessionId(), idempotencyKey);
        assertEquals(OrderState.PAID, order.getState());
        assertNotNull(order.getQueuePosition());
        assertTrue(order.getQueuePosition() > 0);
        assertNotNull(order.getVisualOrderNumber());

        // 4. Vendor: PAID → PREPARING — Req 8.1
        order = orderService.transitionState(orderId, OrderState.PREPARING);
        assertEquals(OrderState.PREPARING, order.getState());

        // 5. Vendor: PREPARING → READY — Req 8.2
        order = orderService.transitionState(orderId, OrderState.READY);
        assertEquals(OrderState.READY, order.getState());
        assertNotNull(order.getPickupWindowStart());

        // 6. Vendor: READY → COLLECTED — Req 8.3
        order = orderService.transitionState(orderId, OrderState.COLLECTED);
        assertEquals(OrderState.COLLECTED, order.getState());

        // Verify persistence — Req 10.1
        CustomerOrder persisted = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderState.COLLECTED, persisted.getState());

        // Verify state history with timestamps — Req 10.2
        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        assertEquals(6, history.size());

        assertStateHistoryEntry(history, 0, null, OrderState.DRAFT);
        assertStateHistoryEntry(history, 1, OrderState.DRAFT, OrderState.AWAITING_PAYMENT);
        assertStateHistoryEntry(history, 2, OrderState.AWAITING_PAYMENT, OrderState.PAID);
        assertStateHistoryEntry(history, 3, OrderState.PAID, OrderState.PREPARING);
        assertStateHistoryEntry(history, 4, OrderState.PREPARING, OrderState.READY);
        assertStateHistoryEntry(history, 5, OrderState.READY, OrderState.COLLECTED);

        // Verify timestamps are chronologically ordered
        for (int i = 1; i < history.size(); i++) {
            assertFalse(history.get(i).getTransitionedAt()
                            .isBefore(history.get(i - 1).getTransitionedAt()),
                    "History timestamps must be chronologically ordered");
        }
    }

    // -----------------------------------------------------------------------
    // Test 2a: Cancel from DRAFT
    // Validates: Req 13.1
    // -----------------------------------------------------------------------

    @Test
    void cancelOrder_fromDraft() {
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(premadeRequest()));
        Long orderId = order.getId();

        // Cancel from DRAFT
        order = orderService.cancelOrder(orderId, session.getSessionId());
        assertEquals(OrderState.CANCELLED, order.getState());

        // Verify persisted
        CustomerOrder persisted = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderState.CANCELLED, persisted.getState());

        // Verify state history
        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        assertEquals(2, history.size());
        assertStateHistoryEntry(history, 0, null, OrderState.DRAFT);
        assertStateHistoryEntry(history, 1, OrderState.DRAFT, OrderState.CANCELLED);
    }

    // -----------------------------------------------------------------------
    // Test 2b: Cancel from AWAITING_PAYMENT (create → checkout → cancel)
    // Validates: Req 13.1
    // -----------------------------------------------------------------------

    @Test
    void cancelOrder_fromAwaitingPayment() {
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(customDrinkRequest()));
        Long orderId = order.getId();

        // Checkout first
        orderService.checkout(orderId, session.getSessionId());

        // Cancel from AWAITING_PAYMENT
        order = orderService.cancelOrder(orderId, session.getSessionId());
        assertEquals(OrderState.CANCELLED, order.getState());

        // Verify state history
        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        assertEquals(3, history.size());
        assertStateHistoryEntry(history, 0, null, OrderState.DRAFT);
        assertStateHistoryEntry(history, 1, OrderState.DRAFT, OrderState.AWAITING_PAYMENT);
        assertStateHistoryEntry(history, 2, OrderState.AWAITING_PAYMENT, OrderState.CANCELLED);
    }

    // -----------------------------------------------------------------------
    // Test 3: Expire order — create → pay → prepare → ready → expired
    // Validates: Req 15.1
    // -----------------------------------------------------------------------

    @Test
    void orderExpiry_readyToExpired() {
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(premadeRequest()));
        Long orderId = order.getId();

        // Progress to READY
        orderService.checkout(orderId, session.getSessionId());
        orderService.confirmPayment(orderId, session.getSessionId(), UUID.randomUUID());
        orderService.transitionState(orderId, OrderState.PREPARING);
        orderService.transitionState(orderId, OrderState.READY);

        // Expire the order (simulates scheduled task)
        orderService.expireOrder(orderId);

        // Verify state
        CustomerOrder persisted = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderState.EXPIRED, persisted.getState());

        // Verify state history
        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        assertEquals(6, history.size());
        assertStateHistoryEntry(history, 0, null, OrderState.DRAFT);
        assertStateHistoryEntry(history, 1, OrderState.DRAFT, OrderState.AWAITING_PAYMENT);
        assertStateHistoryEntry(history, 2, OrderState.AWAITING_PAYMENT, OrderState.PAID);
        assertStateHistoryEntry(history, 3, OrderState.PAID, OrderState.PREPARING);
        assertStateHistoryEntry(history, 4, OrderState.PREPARING, OrderState.READY);
        assertStateHistoryEntry(history, 5, OrderState.READY, OrderState.EXPIRED);

        // Verify the EXPIRED transition was triggered by SYSTEM
        OrderStateHistory expiryEntry = history.get(5);
        assertEquals(TransitionTrigger.SYSTEM, expiryEntry.getTriggeredBy());
    }

    // -----------------------------------------------------------------------
    // Test 4: Session expiry cancels DRAFT orders
    // Validates: Req 14.4, 14.5
    // -----------------------------------------------------------------------

    @Test
    void sessionExpiry_cancelsDraftOrders() {
        // Create a DRAFT order
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(premadeRequest()));
        Long orderId = order.getId();
        assertEquals(OrderState.DRAFT, order.getState());

        // Simulate session inactivity by setting last_activity_at to 3 hours ago
        CustomerSession dbSession = customerSessionRepository.findBySessionId(session.getSessionId())
                .orElseThrow();
        dbSession.setLastActivityAt(LocalDateTime.now().minusHours(3));
        customerSessionRepository.save(dbSession);

        // Trigger session expiry (simulates scheduled task)
        sessionService.expireInactiveSessions();

        // Verify session is expired
        CustomerSession expiredSession = customerSessionRepository.findBySessionId(session.getSessionId())
                .orElseThrow();
        assertTrue(expiredSession.isExpired());

        // Verify DRAFT order was cancelled
        CustomerOrder persisted = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderState.CANCELLED, persisted.getState());

        // Verify state history includes SYSTEM-triggered cancellation
        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        OrderStateHistory cancellationEntry = history.stream()
                .filter(h -> h.getToState() == OrderState.CANCELLED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CANCELLED history entry found"));
        assertEquals(OrderState.DRAFT, cancellationEntry.getFromState());
        assertEquals(TransitionTrigger.SYSTEM, cancellationEntry.getTriggeredBy());
        assertNotNull(cancellationEntry.getTransitionedAt());
    }

    // -----------------------------------------------------------------------
    // Test 5: All state transitions have timestamps recorded
    // Validates: Req 10.2
    // -----------------------------------------------------------------------

    @Test
    void allStateTransitions_haveTimestampsRecorded() {
        CustomerOrder order = orderService.createOrder(
                station.getId(), session.getSessionId(), List.of(customDrinkRequest()));
        Long orderId = order.getId();

        orderService.checkout(orderId, session.getSessionId());
        orderService.confirmPayment(orderId, session.getSessionId(), UUID.randomUUID());
        orderService.transitionState(orderId, OrderState.PREPARING);
        orderService.transitionState(orderId, OrderState.READY);
        orderService.transitionState(orderId, OrderState.COLLECTED);

        List<OrderStateHistory> history = orderStateHistoryRepository.findByOrderId(orderId);
        assertEquals(6, history.size());

        // Every single entry must have a non-null timestamp
        for (OrderStateHistory entry : history) {
            assertNotNull(entry.getTransitionedAt(),
                    "Transition to " + entry.getToState() + " must have a timestamp");
            assertNotNull(entry.getToState(), "toState must not be null");
            assertNotNull(entry.getTriggeredBy(), "triggeredBy must not be null");
        }

        // Verify trigger types are correct
        assertEquals(TransitionTrigger.CUSTOMER, history.get(0).getTriggeredBy()); // DRAFT
        assertEquals(TransitionTrigger.CUSTOMER, history.get(1).getTriggeredBy()); // AWAITING_PAYMENT
        assertEquals(TransitionTrigger.CUSTOMER, history.get(2).getTriggeredBy()); // PAID
        assertEquals(TransitionTrigger.VENDOR, history.get(3).getTriggeredBy());   // PREPARING
        assertEquals(TransitionTrigger.VENDOR, history.get(4).getTriggeredBy());   // READY
        assertEquals(TransitionTrigger.VENDOR, history.get(5).getTriggeredBy());   // COLLECTED
    }

    // Inner class for assertion error in lambda
    private static class AssertionError extends RuntimeException {
        AssertionError(String message) {
            super(message);
        }
    }
}
