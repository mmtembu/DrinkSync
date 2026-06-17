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

        // Clean up test data
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

    private StompSession connectAndTrack() throws Exception {
        StompSession stompSession = stompClient
                .connectAsync(wsUrl(), new NoOpStompSessionHandler())
                .get(5, TimeUnit.SECONDS);
        activeSessions.add(stompSession);
        return stompSession;
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
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe(
                "/topic/stations/" + station.getId() + "/orders",
                new OrderResponseFrameHandler(messages));

        // Allow subscription to register
        Thread.sleep(500);

        // Trigger a state transition — this should broadcast to the station topic
        orderService.transitionState(order.getId(), OrderState.PREPARING);

        // Verify message received within 1 second
        OrderResponse received = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(received, "Should receive state change on station topic within 1 second");
        assertEquals(order.getId(), received.getId());
        assertEquals(OrderState.PREPARING, received.getState());
        assertEquals(station.getId(), received.getStationId());
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
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe(
                "/topic/orders/" + order.getId(),
                new OrderResponseFrameHandler(messages));

        // Allow subscription to register
        Thread.sleep(500);

        // Trigger a state transition
        orderService.transitionState(order.getId(), OrderState.PREPARING);

        // Verify message received within 1 second
        OrderResponse received = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(received, "Should receive state change on order topic within 1 second");
        assertEquals(order.getId(), received.getId());
        assertEquals(OrderState.PREPARING, received.getState());
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
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe(
                "/topic/stations/" + station.getId() + "/orders",
                new OrderResponseFrameHandler(messages));

        // Allow subscription to register
        Thread.sleep(500);

        // Transition PAID → PREPARING
        orderService.transitionState(order.getId(), OrderState.PREPARING);
        OrderResponse preparingMsg = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(preparingMsg, "Should receive PREPARING state on station topic within 1 second");
        assertEquals(OrderState.PREPARING, preparingMsg.getState());
        assertEquals(order.getId(), preparingMsg.getId());

        // Transition PREPARING → READY
        orderService.transitionState(order.getId(), OrderState.READY);
        OrderResponse readyMsg = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(readyMsg, "Should receive READY state on station topic within 1 second");
        assertEquals(OrderState.READY, readyMsg.getState());
        assertEquals(order.getId(), readyMsg.getId());

        // Transition READY → COLLECTED
        orderService.transitionState(order.getId(), OrderState.COLLECTED);
        OrderResponse collectedMsg = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(collectedMsg, "Should receive COLLECTED state on station topic within 1 second");
        assertEquals(OrderState.COLLECTED, collectedMsg.getState());
        assertEquals(order.getId(), collectedMsg.getId());
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
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe(
                "/topic/orders/" + order1.getId(),
                new OrderResponseFrameHandler(messages));

        // Allow subscription to register
        Thread.sleep(500);

        // Transition order2 — should NOT appear on order1's topic
        orderService.transitionState(order2.getId(), OrderState.PREPARING);

        // Wait briefly — should NOT receive anything
        OrderResponse unexpected = messages.poll(500, TimeUnit.MILLISECONDS);
        assertNull(unexpected, "Should NOT receive updates for a different order on per-order topic");

        // Transition order1 — SHOULD appear
        orderService.transitionState(order1.getId(), OrderState.PREPARING);
        OrderResponse expected = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(expected, "Should receive update for subscribed order");
        assertEquals(order1.getId(), expected.getId());
        assertEquals(OrderState.PREPARING, expected.getState());
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
        BlockingQueue<OrderResponse> messages = new LinkedBlockingQueue<>();

        stompSession.subscribe(
                "/topic/stations/" + station.getId() + "/orders",
                new OrderResponseFrameHandler(messages));

        // Allow subscription to register
        Thread.sleep(500);

        // Transition order1 to PREPARING
        orderService.transitionState(order1.getId(), OrderState.PREPARING);
        OrderResponse msg1 = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(msg1, "Station topic should receive update for order1");
        assertEquals(order1.getId(), msg1.getId());
        assertEquals(OrderState.PREPARING, msg1.getState());

        // Transition order2 to PREPARING
        orderService.transitionState(order2.getId(), OrderState.PREPARING);
        OrderResponse msg2 = messages.poll(1, TimeUnit.SECONDS);
        assertNotNull(msg2, "Station topic should receive update for order2");
        assertEquals(order2.getId(), msg2.getId());
        assertEquals(OrderState.PREPARING, msg2.getState());
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
     * No-op session handler for the STOMP connection.
     */
    private static class NoOpStompSessionHandler extends StompSessionHandlerAdapter {
        @Override
        public void handleException(StompSession session, StompCommand command,
                                    StompHeaders headers, byte[] payload, Throwable exception) {
            // Log but don't fail — test assertions handle verification
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            // Log but don't fail — test assertions handle verification
        }
    }
}
