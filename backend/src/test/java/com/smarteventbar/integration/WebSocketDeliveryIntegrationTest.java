package com.smarteventbar.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.dto.OrderResponse;
import com.smarteventbar.model.entity.*;
import com.smarteventbar.model.enums.CupOption;
import com.smarteventbar.model.enums.OrderItemType;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.*;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket delivery of order state changes.
 * <p>
 * Uses a real embedded server (RANDOM_PORT) with a STOMP WebSocket client
 * to verify that order state transitions are broadcast to subscribed clients
 * on both per-station and per-order topics within 1 second.
 * <p>
 * Validates: Requirements 5.1, 7.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketDeliveryIntegrationTest {

    // --- Timeout constants ---
    private static final long CONNECTION_TIMEOUT_SECONDS = 5;
    private static final long DELIVERY_SLA_SECONDS = 1;
    private static final long NO_MESSAGE_WAIT_MS = 200;
    private static final long SUBSCRIPTION_PROPAGATION_MS = 200;
    private static final long ISOLATION_WAIT_MS = 500;

    @LocalServerPort
    private int port;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;
    private Station station;
    private SpiritItem spirit;
    private MixerItem mixer;
    private PremadeItem premade;
    private CustomerSession session;
    private final List<StompSession> activeSessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Set up STOMP client with SockJS transport
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        SockJsClient sockJsClient = new SockJsClient(transports);
        stompClient = new WebSocketStompClient(sockJsClient);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        // Create test data
        String accessCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        station = stationRepository.save(
                new Station("WS Test Bar", "WebSocket Test Area", new BigDecimal("5.00"),
                        accessCode, 10));

        spirit = spiritItemRepository.save(
                new SpiritItem(station, "Vodka", new BigDecimal("30.00"), true));

        mixer = mixerItemRepository.save(
                new MixerItem(station, "Lemonade", new BigDecimal("10.00"), true));

        premade = premadeItemRepository.save(
                new PremadeItem(station, "Craft Beer", "Local IPA", new BigDecimal("45.00"), true));

        session = sessionService.createSession(station.getId());
    }

    @AfterEach
    void tearDown() {
        // Disconnect all STOMP sessions
        for (StompSession s : activeSessions) {
            if (s.isConnected()) {
                s.disconnect();
            }
        }
        activeSessions.clear();

        if (stompClient != null) {
            stompClient.stop();
        }

        // Note: @Transactional rollback is not viable with RANDOM_PORT because
        // the server runs in a separate thread with its own transaction boundaries.
        // We use explicit cleanup in FK-dependency order instead.
        cleanUpTestData();
    }

    private void cleanUpTestData() {
        Long stationId = station.getId();
        jdbcTemplate.update(
                "DELETE FROM idempotency_key WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update(
                "DELETE FROM order_state_history WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update(
                "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE station_id = ?)",
                stationId);
        jdbcTemplate.update("DELETE FROM orders WHERE station_id = ?", stationId);
    }

    // --- Helper methods ---

    private String wsUrl() {
        return "ws://localhost:" + port + "/ws";
    }

    private String stationTopic(Long stationId) {
        return "/topic/stations/" + stationId + "/orders";
    }

    private String orderTopic(Long orderId) {
        return "/topic/orders/" + orderId;
    }

    private StompSession connectAndTrack() throws Exception {
        StompSession stompSession = stompClient
                .connectAsync(wsUrl(), new NoOpStompSessionHandler())
                .get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        activeSessions.add(stompSession);
        return stompSession;
    }

    /**
     * Subscribes to the given topic and returns a BlockingQueue that will receive messages.
     * Uses a short deterministic wait for subscription propagation since SimpleBroker
     * (in-memory) does not support STOMP receipts.
     */
    private BlockingQueue<OrderResponse> subscribeWithConfirmation(StompSession stompSession, String topic)
            throws InterruptedException {
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        StompHeaders headers = new StompHeaders();
        headers.setDestination(topic);

        stompSession.subscribe(headers, new OrderResponseFrameHandler(messages));

        // SimpleBroker (in-memory) does not support STOMP receipts, so we use
        // a short deterministic wait for subscription propagation instead
        Thread.sleep(SUBSCRIPTION_PROPAGATION_MS);
        return messages;
    }

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
                station.getId(), session.getSessionId(), List.of(premadeRequest()));
        orderService.checkout(order.getId(), session.getSessionId());
        return orderService.confirmPayment(order.getId(), session.getSessionId(), UUID.randomUUID());
    }

    // -----------------------------------------------------------------------
    // Test 1: Per-station subscription receives state change within 1 second
    // Validates: Req 5.1, 7.1
    // -----------------------------------------------------------------------

    @Test
    void perStationSubscription_receivesStateChangeWithin1Second() throws Exception {
        // Create and pay an order BEFORE subscribing to avoid draining
        // the PAID broadcast which can arrive with variable timing
        CustomerOrder order = createAndPayOrder();

        // Connect and subscribe to station topic
        StompSession stompSession = connectAndTrack();
        BlockingQueue<OrderResponse> messages = subscribeWithConfirmation(
                stompSession, stationTopic(station.getId()));

        // Trigger a state transition — this should broadcast to the station topic
        orderService.transitionState(order.getId(), OrderState.PREPARING);

        // Verify message received within 1 second
        OrderResponse received = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(received, "Should receive state change on station topic within 1 second");
        assertEquals(order.getId(), received.getId());
        assertEquals(OrderState.PREPARING, received.getState());
        assertEquals(station.getId(), received.getStationId());

        // Confirm no unexpected additional messages
        assertNull(messages.poll(NO_MESSAGE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Should not receive unexpected additional messages on station topic");
    }

    // -----------------------------------------------------------------------
    // Test 2: Per-order subscription receives state change within 1 second
    // Validates: Req 5.1
    // -----------------------------------------------------------------------

    @Test
    void perOrderSubscription_receivesStateChangeWithin1Second() throws Exception {
        // Create and pay an order first (need the order ID to subscribe)
        CustomerOrder order = createAndPayOrder();

        // Connect and subscribe to order-specific topic
        StompSession stompSession = connectAndTrack();
        BlockingQueue<OrderResponse> messages = subscribeWithConfirmation(
                stompSession, orderTopic(order.getId()));

        // Trigger a state transition
        orderService.transitionState(order.getId(), OrderState.PREPARING);

        // Verify message received within 1 second
        OrderResponse received = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(received, "Should receive state change on order topic within 1 second");
        assertEquals(order.getId(), received.getId());
        assertEquals(OrderState.PREPARING, received.getState());

        // Confirm no unexpected additional messages
        assertNull(messages.poll(NO_MESSAGE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Should not receive unexpected additional messages on order topic");
    }

    // -----------------------------------------------------------------------
    // Test 3: Per-station subscription receives updates for multiple transitions
    // Validates: Req 5.1, 7.1
    // -----------------------------------------------------------------------

    @Test
    void perStationSubscription_receivesMultipleTransitions() throws Exception {
        // Create and pay an order BEFORE subscribing so we don't need to drain
        // the AWAITING_PAYMENT and PAID broadcasts from the order creation flow
        CustomerOrder order = createAndPayOrder();

        // Connect and subscribe to station topic
        StompSession stompSession = connectAndTrack();
        BlockingQueue<OrderResponse> messages = subscribeWithConfirmation(
                stompSession, stationTopic(station.getId()));

        // Transition PAID → PREPARING
        orderService.transitionState(order.getId(), OrderState.PREPARING);
        OrderResponse preparingMsg = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(preparingMsg, "Should receive PREPARING state on station topic within 1 second");
        assertEquals(OrderState.PREPARING, preparingMsg.getState());
        assertEquals(order.getId(), preparingMsg.getId());

        // Transition PREPARING → READY
        orderService.transitionState(order.getId(), OrderState.READY);
        OrderResponse readyMsg = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(readyMsg, "Should receive READY state on station topic within 1 second");
        assertEquals(OrderState.READY, readyMsg.getState());
        assertEquals(order.getId(), readyMsg.getId());

        // Transition READY → COLLECTED
        orderService.transitionState(order.getId(), OrderState.COLLECTED);
        OrderResponse collectedMsg = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(collectedMsg, "Should receive COLLECTED state on station topic within 1 second");
        assertEquals(OrderState.COLLECTED, collectedMsg.getState());
        assertEquals(order.getId(), collectedMsg.getId());

        // Confirm no unexpected additional messages
        assertNull(messages.poll(NO_MESSAGE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Should not receive unexpected additional messages after all transitions");
    }

    // -----------------------------------------------------------------------
    // Test 4: Per-order subscription does not receive updates for other orders
    // Validates: Req 5.1
    // -----------------------------------------------------------------------

    @Test
    void perOrderSubscription_doesNotReceiveUpdatesForOtherOrders() throws Exception {
        // Create two orders
        CustomerOrder order1 = createAndPayOrder();
        CustomerOrder order2 = createAndPayOrder();

        // Subscribe only to order1's topic
        StompSession stompSession = connectAndTrack();
        BlockingQueue<OrderResponse> messages = subscribeWithConfirmation(
                stompSession, orderTopic(order1.getId()));

        // Transition order2 — should NOT appear on order1's topic
        orderService.transitionState(order2.getId(), OrderState.PREPARING);

        // Wait briefly — should NOT receive anything
        OrderResponse unexpected = messages.poll(ISOLATION_WAIT_MS, TimeUnit.MILLISECONDS);
        assertNull(unexpected, "Should NOT receive updates for a different order on per-order topic");

        // Transition order1 — SHOULD appear
        orderService.transitionState(order1.getId(), OrderState.PREPARING);
        OrderResponse expected = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(expected, "Should receive update for subscribed order");
        assertEquals(order1.getId(), expected.getId());
        assertEquals(OrderState.PREPARING, expected.getState());

        // Confirm no unexpected additional messages
        assertNull(messages.poll(NO_MESSAGE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Should not receive unexpected additional messages on per-order topic");
    }

    // -----------------------------------------------------------------------
    // Test 5: Per-station subscription receives updates from different orders
    // Validates: Req 7.1
    // -----------------------------------------------------------------------

    @Test
    void perStationSubscription_receivesUpdatesFromDifferentOrders() throws Exception {
        // Create and pay two orders BEFORE subscribing to avoid draining
        // the AWAITING_PAYMENT and PAID broadcasts from the creation flow
        CustomerOrder order1 = createAndPayOrder();
        CustomerOrder order2 = createAndPayOrder();

        // Connect and subscribe to station topic
        StompSession stompSession = connectAndTrack();
        BlockingQueue<OrderResponse> messages = subscribeWithConfirmation(
                stompSession, stationTopic(station.getId()));

        // Transition order1 to PREPARING
        orderService.transitionState(order1.getId(), OrderState.PREPARING);
        OrderResponse msg1 = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(msg1, "Station topic should receive update for order1");
        assertEquals(order1.getId(), msg1.getId());
        assertEquals(OrderState.PREPARING, msg1.getState());

        // Transition order2 to PREPARING
        orderService.transitionState(order2.getId(), OrderState.PREPARING);
        OrderResponse msg2 = messages.poll(DELIVERY_SLA_SECONDS, TimeUnit.SECONDS);
        assertNotNull(msg2, "Station topic should receive update for order2");
        assertEquals(order2.getId(), msg2.getId());
        assertEquals(OrderState.PREPARING, msg2.getState());

        // Confirm no unexpected additional messages
        assertNull(messages.poll(NO_MESSAGE_WAIT_MS, TimeUnit.MILLISECONDS),
                "Should not receive unexpected additional messages on station topic");
    }

    // --- STOMP frame handler ---

    /**
     * Handles incoming STOMP frames and puts deserialized OrderResponse objects
     * into a blocking queue for test assertions.
     */
    private static class OrderResponseFrameHandler implements StompFrameHandler {

        private final BlockingQueue<OrderResponse> queue;

        OrderResponseFrameHandler(BlockingQueue<OrderResponse> queue) {
            this.queue = queue;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return OrderResponse.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            queue.offer((OrderResponse) payload);
        }
    }

    /**
     * Session handler that logs transport and protocol errors for debugging.
     */
    private static class NoOpStompSessionHandler extends StompSessionHandlerAdapter {

        private static final Logger log = LoggerFactory.getLogger(NoOpStompSessionHandler.class);

        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            log.warn("STOMP protocol error [command={}]: {}", command, exception.getMessage(), exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.warn("STOMP transport error: {}", exception.getMessage(), exception);
        }
    }
}
