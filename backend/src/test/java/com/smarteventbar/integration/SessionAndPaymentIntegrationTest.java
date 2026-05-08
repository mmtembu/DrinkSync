package com.smarteventbar.integration;

import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.dto.PaymentResult;
import com.smarteventbar.exception.MaxConcurrentOrdersException;
import com.smarteventbar.exception.StationLockException;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.service.PaymentService;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for session-station lock enforcement, concurrent order limits,
 * and payment idempotency.
 * <p>
 * Uses the "integration" profile which configures PostgreSQL via Testcontainers or
 * a pre-started instance.
 * <p>
 * Validates: Requirements 16.4, 16.6, 16.8, 18.2, 18.3, 18.4
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class SessionAndPaymentIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private PaymentService paymentService;

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
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private CustomerSessionRepository customerSessionRepository;

    private Station stationA;
    private Station stationB;
    private SpiritItem spirit;
    private MixerItem mixer;
    private PremadeItem premade;
    private CustomerSession session;

    @BeforeEach
    void setUp() {
        stationA = stationRepository.save(
                new Station("Bar Alpha", "Main Stage", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        stationB = stationRepository.save(
                new Station("Bar Beta", "VIP Area", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        spirit = spiritItemRepository.save(
                new SpiritItem(stationA, "Vodka", new BigDecimal("30.00"), true));

        mixer = mixerItemRepository.save(
                new MixerItem(stationA, "Lemonade", new BigDecimal("10.00"), true));

        premade = premadeItemRepository.save(
                new PremadeItem(stationA, "Craft Beer", "Local IPA", new BigDecimal("45.00"), true));

        // Also add menu items to station B so orders can be created there
        spiritItemRepository.save(
                new SpiritItem(stationB, "Gin", new BigDecimal("35.00"), true));
        mixerItemRepository.save(
                new MixerItem(stationB, "Tonic", new BigDecimal("8.00"), true));

        session = sessionService.createSession(stationA.getId());
    }

    // --- Helper methods ---

    private OrderItemRequest premadeRequest() {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemType(OrderItemType.PREMADE);
        req.setPremadeItemId(premade.getId());
        req.setQuantity(1);
        return req;
    }

    private OrderItemRequest customDrinkRequest() {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemType(OrderItemType.CUSTOM_DRINK);
        req.setSpiritItemIds(java.util.List.of(spirit.getId()));
        req.setMixerItemIds(java.util.List.of(mixer.getId()));
        req.setCupOption(CupOption.NEW_CUP);
        req.setQuantity(1);
        return req;
    }

    private CustomerOrder createAndPayOrder() {
        CustomerOrder order = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        orderService.checkout(order.getId(), session.getSessionId());
        return orderService.confirmPayment(order.getId(), session.getSessionId(), UUID.randomUUID());
    }

    private void completeOrder(CustomerOrder order) {
        orderService.transitionState(order.getId(), OrderState.PREPARING);
        orderService.transitionState(order.getId(), OrderState.READY);
        orderService.transitionState(order.getId(), OrderState.COLLECTED);
    }

    // -----------------------------------------------------------------------
    // Test 1: Station lock enforcement and release
    // Validates: Req 16.6, 16.8
    // -----------------------------------------------------------------------

    @Test
    void stationLock_rejectsOrderAtDifferentStation_thenAllowsAfterCompletion() {
        // Create an order at station A (non-terminal DRAFT state)
        CustomerOrder orderAtA = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        assertNotNull(orderAtA.getId());
        assertEquals(OrderState.DRAFT, orderAtA.getState());

        // Attempt to create an order at station B — should be rejected (station lock)
        assertThrows(StationLockException.class, () ->
                orderService.createOrder(stationB.getId(), session.getSessionId(),
                        List.of(premadeRequest())));

        // Cancel the order at station A (moves to terminal state)
        orderService.cancelOrder(orderAtA.getId(), session.getSessionId());
        assertEquals(OrderState.CANCELLED,
                orderRepository.findById(orderAtA.getId()).orElseThrow().getState());

        // Now ordering at station B should succeed (session released)
        // Need to create a premade item at station B for the order
        PremadeItem premadeB = premadeItemRepository.save(
                new PremadeItem(stationB, "Cider", "Apple Cider", new BigDecimal("40.00"), true));

        OrderItemRequest stationBRequest = new OrderItemRequest();
        stationBRequest.setItemType(OrderItemType.PREMADE);
        stationBRequest.setPremadeItemId(premadeB.getId());
        stationBRequest.setQuantity(1);

        CustomerOrder orderAtB = orderService.createOrder(
                stationB.getId(), session.getSessionId(), List.of(stationBRequest));
        assertNotNull(orderAtB.getId());
        assertEquals(OrderState.DRAFT, orderAtB.getState());
    }

    // -----------------------------------------------------------------------
    // Test 2: Concurrent order limit (3 max, then reject, then allow)
    // Validates: Req 16.4
    // -----------------------------------------------------------------------

    @Test
    void concurrentOrderLimit_rejectsAfterThree_thenAllowsAfterCompletion() {
        // Create 3 orders (all in non-terminal states)
        CustomerOrder order1 = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        CustomerOrder order2 = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        CustomerOrder order3 = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));

        assertEquals(3, sessionService.getActiveOrderCount(session.getSessionId()));

        // 4th order should be rejected
        assertThrows(MaxConcurrentOrdersException.class, () ->
                orderService.createOrder(stationA.getId(), session.getSessionId(),
                        List.of(premadeRequest())));

        // Complete one order (move to terminal state: COLLECTED)
        orderService.checkout(order1.getId(), session.getSessionId());
        orderService.confirmPayment(order1.getId(), session.getSessionId(), UUID.randomUUID());
        completeOrder(order1);

        assertEquals(OrderState.COLLECTED,
                orderRepository.findById(order1.getId()).orElseThrow().getState());
        assertEquals(2, sessionService.getActiveOrderCount(session.getSessionId()));

        // Now a new order should succeed
        CustomerOrder order4 = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        assertNotNull(order4.getId());
        assertEquals(OrderState.DRAFT, order4.getState());
        assertEquals(3, sessionService.getActiveOrderCount(session.getSessionId()));
    }

    // -----------------------------------------------------------------------
    // Test 3: Payment idempotency — duplicate key returns original response
    // Validates: Req 18.2, 18.3
    // -----------------------------------------------------------------------

    @Test
    void paymentIdempotency_duplicateKeyReturnsSameOrder() {
        // Create and checkout an order
        CustomerOrder order = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(customDrinkRequest()));
        orderService.checkout(order.getId(), session.getSessionId());

        UUID idempotencyKey = UUID.randomUUID();

        // First payment — should succeed and transition to PAID
        CustomerOrder firstResult = orderService.confirmPayment(
                order.getId(), session.getSessionId(), idempotencyKey);
        assertEquals(OrderState.PAID, firstResult.getState());
        assertNotNull(firstResult.getQueuePosition());
        assertNotNull(firstResult.getVisualOrderNumber());

        Long orderId = firstResult.getId();
        int queuePosition = firstResult.getQueuePosition();
        String visualOrderNumber = firstResult.getVisualOrderNumber();

        // Second payment with same idempotency key — should return the same order
        // without reprocessing (order is already PAID, so it just returns the existing order)
        CustomerOrder secondResult = orderService.confirmPayment(
                order.getId(), session.getSessionId(), idempotencyKey);
        assertEquals(OrderState.PAID, secondResult.getState());
        assertEquals(orderId, secondResult.getId());
        assertEquals(queuePosition, secondResult.getQueuePosition());
        assertEquals(visualOrderNumber, secondResult.getVisualOrderNumber());
    }

    // -----------------------------------------------------------------------
    // Test 3b: Payment idempotency via PaymentService directly
    // Validates: Req 18.2
    // -----------------------------------------------------------------------

    @Test
    void paymentIdempotency_paymentServiceReturnsCachedResult() {
        // Create, checkout, and pay an order
        CustomerOrder order = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        orderService.checkout(order.getId(), session.getSessionId());

        UUID idempotencyKey = UUID.randomUUID();

        // Process payment via PaymentService
        PaymentResult firstResult = paymentService.processPayment(order, idempotencyKey);
        assertTrue(firstResult.isSuccess());
        assertEquals(200, firstResult.getStatusCode());

        // Process again with same key — should return cached result
        PaymentResult secondResult = paymentService.processPayment(order, idempotencyKey);
        assertTrue(secondResult.isSuccess());
        assertEquals(firstResult.getStatusCode(), secondResult.getStatusCode());
        assertEquals(firstResult.getOrderId(), secondResult.getOrderId());
        assertEquals(firstResult.getMessage(), secondResult.getMessage());
    }

    // -----------------------------------------------------------------------
    // Test 4: Idempotency key expiry — key older than 24 hours treated as expired
    // Validates: Req 18.4
    // -----------------------------------------------------------------------

    @Test
    void idempotencyKeyExpiry_expiredKeyTreatedAsNew() {
        // Create and checkout an order
        CustomerOrder order = orderService.createOrder(
                stationA.getId(), session.getSessionId(), List.of(premadeRequest()));
        orderService.checkout(order.getId(), session.getSessionId());

        UUID idempotencyKey = UUID.randomUUID();

        // Process payment via PaymentService
        PaymentResult firstResult = paymentService.processPayment(order, idempotencyKey);
        assertTrue(firstResult.isSuccess());

        // Manually expire the idempotency key by setting expires_at to the past
        IdempotencyKey storedKey = idempotencyKeyRepository
                .findByOrderIdAndIdempotencyKey(order.getId(), idempotencyKey)
                .orElseThrow(() -> new AssertionError("Idempotency key should exist"));

        storedKey.setExpiresAt(LocalDateTime.now().minusHours(1));
        storedKey.setCreatedAt(LocalDateTime.now().minusHours(25));
        idempotencyKeyRepository.save(storedKey);

        // Looking up the expired key should return empty
        Optional<PaymentResult> lookupResult = paymentService.findByIdempotencyKey(
                order.getId(), idempotencyKey);
        assertTrue(lookupResult.isEmpty(), "Expired idempotency key should not return a cached result");
    }

    // Inner class for assertion error in lambda
    private static class AssertionError extends RuntimeException {
        AssertionError(String message) {
            super(message);
        }
    }
}
