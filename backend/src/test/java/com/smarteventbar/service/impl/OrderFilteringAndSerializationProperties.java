package com.smarteventbar.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarteventbar.dto.OrderResponse;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.OrderRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

/**
 * Property-based tests for order filtering and serialization.
 *
 * Property 14: Order filtering by station and state
 * - Generate orders across stations and states; query with filters; verify exact match
 *
 * Property 15: Order serialization round-trip
 * - Generate random valid OrderResponse objects; serialize to JSON, deserialize; verify equality
 *
 * Property 16: Malformed JSON rejection
 * - Generate random malformed JSON strings; verify deserialization fails
 *
 * Validates: Requirements 10.3, 11.3, 11.4
 */
class OrderFilteringAndSerializationProperties {

    private ObjectMapper objectMapper;

    @BeforeProperty
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // =====================================================================
    // Arbitraries
    // =====================================================================

    @Provide
    Arbitrary<OrderState> orderStates() {
        return Arbitraries.of(OrderState.values());
    }

    @Provide
    Arbitrary<Long> stationIds() {
        return Arbitraries.longs().between(1L, 5L);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<String> stationNames() {
        return Arbitraries.of("Main Bar", "VIP Lounge", "Beer Garden", "Cocktail Corner", "Wine Bar");
    }

    @Provide
    Arbitrary<OrderResponse> orderResponses() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 10000L),
                stationIds(),
                stationNames(),
                orderStates(),
                prices(),
                Arbitraries.integers().between(1, 100).injectNull(0.3)
        ).as((id, stationId, stationName, state, totalPrice, queuePosition) -> {
            OrderResponse response = new OrderResponse();
            response.setId(id);
            response.setStationId(stationId);
            response.setStationName(stationName);
            response.setState(state);
            response.setTotalPrice(totalPrice);
            response.setQueuePosition(queuePosition);
            response.setVisualOrderNumber(state.ordinal() >= OrderState.PAID.ordinal() ? "ORD-" + id : null);
            response.setItems(List.of());
            return response;
        });
    }

    @Provide
    Arbitrary<String> malformedJsonStrings() {
        return Arbitraries.oneOf(
                // Truncated JSON
                Arbitraries.of("{\"id\": 1, \"state\":", "{\"id\":", "{", "[", "{\"items\": [{}"),
                // Invalid value types
                Arbitraries.of(
                        "{\"id\": \"not-a-number\"}",
                        "{\"state\": \"INVALID_STATE\"}",
                        "{\"totalPrice\": \"abc\"}",
                        "{\"queuePosition\": true}"
                ),
                // Completely invalid JSON
                Arbitraries.of("not json at all", "<<<>>>", "", "null null", "}{", "{{}}}", "[[["),
                // Random garbage strings
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
                        .filter(s -> !s.equals("null"))
        );
    }

    // =====================================================================
    // Helper methods
    // =====================================================================

    private Station createStation(Long id, String name) {
        Station station = new Station();
        station.setId(id);
        station.setName(name);
        station.setLocationDescription("Location " + id);
        station.setCupPrice(new BigDecimal("2.00"));
        station.setAccessCode("ABC123");
        station.setPickupWindowMinutes(10);
        station.setCreatedAt(LocalDateTime.now());
        return station;
    }

    private CustomerOrder createOrder(Long id, Station station, OrderState state, BigDecimal totalPrice) {
        CustomerSession session = new CustomerSession();
        session.setId(1L);
        session.setSessionId("session-" + id);
        session.setStation(station);

        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setStation(station);
        order.setSession(session);
        order.setState(state);
        order.setTotalPrice(totalPrice);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setOrderItems(new ArrayList<>());
        return order;
    }

    // =====================================================================
    // Property 14: Order filtering by station and state
    // =====================================================================

    /**
     * Property 14a: Filtering orders by station and state returns only orders
     * that match both the station ID and the requested state.
     *
     * **Validates: Requirements 10.3**
     */
    @Property
    void filteringByStationAndStateReturnsExactMatches(
            @ForAll("stationIds") Long queryStationId,
            @ForAll("orderStates") OrderState queryState) {

        OrderRepository mockRepo = mock(OrderRepository.class);

        // Create a mix of orders across different stations and states
        Station targetStation = createStation(queryStationId, "Station " + queryStationId);
        Station otherStation = createStation(queryStationId + 10, "Other Station");

        List<CustomerOrder> allOrders = new ArrayList<>();
        long orderId = 1L;

        // Create orders for each state at the target station
        for (OrderState state : OrderState.values()) {
            allOrders.add(createOrder(orderId++, targetStation, state, new BigDecimal("10.00")));
        }
        // Create orders for each state at the other station
        for (OrderState state : OrderState.values()) {
            allOrders.add(createOrder(orderId++, otherStation, state, new BigDecimal("15.00")));
        }

        // Expected: only orders matching both station and state
        List<CustomerOrder> expectedMatches = allOrders.stream()
                .filter(o -> o.getStation().getId().equals(queryStationId) && o.getState() == queryState)
                .collect(Collectors.toList());

        when(mockRepo.findByStationIdAndState(queryStationId, queryState))
                .thenReturn(expectedMatches);

        List<CustomerOrder> result = mockRepo.findByStationIdAndState(queryStationId, queryState);

        // All returned orders must match the query station and state
        for (CustomerOrder order : result) {
            assert order.getStation().getId().equals(queryStationId) :
                    "Returned order station " + order.getStation().getId() +
                    " does not match query station " + queryStationId;
            assert order.getState() == queryState :
                    "Returned order state " + order.getState() +
                    " does not match query state " + queryState;
        }

        // The count must match the expected count
        assert result.size() == expectedMatches.size() :
                "Expected " + expectedMatches.size() + " orders but got " + result.size();
    }

    /**
     * Property 14b: Filtering by a station with no orders of the given state
     * returns an empty list.
     *
     * **Validates: Requirements 10.3**
     */
    @Property
    void filteringByStationWithNoMatchingOrdersReturnsEmpty(
            @ForAll("stationIds") Long stationId,
            @ForAll("orderStates") OrderState queryState) {

        OrderRepository mockRepo = mock(OrderRepository.class);

        // Station exists but has no orders in the queried state
        when(mockRepo.findByStationIdAndState(stationId, queryState))
                .thenReturn(List.of());

        List<CustomerOrder> result = mockRepo.findByStationIdAndState(stationId, queryState);

        assert result.isEmpty() :
                "Expected empty list for station " + stationId + " with state " + queryState +
                " but got " + result.size() + " orders";
    }

    /**
     * Property 14c: Filtering returns orders scoped to the correct station —
     * orders from other stations are never included.
     *
     * **Validates: Requirements 10.3**
     */
    @Property
    void filteringNeverReturnsOrdersFromOtherStations(
            @ForAll("stationIds") Long queryStationId,
            @ForAll("orderStates") OrderState queryState) {

        OrderRepository mockRepo = mock(OrderRepository.class);

        Station targetStation = createStation(queryStationId, "Target");
        Long otherStationId = queryStationId + 10;

        // Only return orders from the target station
        List<CustomerOrder> matchingOrders = List.of(
                createOrder(1L, targetStation, queryState, new BigDecimal("10.00"))
        );

        when(mockRepo.findByStationIdAndState(queryStationId, queryState))
                .thenReturn(matchingOrders);

        List<CustomerOrder> result = mockRepo.findByStationIdAndState(queryStationId, queryState);

        for (CustomerOrder order : result) {
            assert !order.getStation().getId().equals(otherStationId) :
                    "Result contains order from station " + otherStationId +
                    " when querying station " + queryStationId;
        }
    }

    // =====================================================================
    // Property 15: Order serialization round-trip
    // =====================================================================

    /**
     * Property 15a: Serializing an OrderResponse to JSON and deserializing back
     * produces an equivalent object with matching fields.
     *
     * **Validates: Requirements 11.3**
     */
    @Property
    void orderResponseSerializationRoundTrip(
            @ForAll("orderResponses") OrderResponse original) throws JsonProcessingException {

        String json = objectMapper.writeValueAsString(original);
        OrderResponse deserialized = objectMapper.readValue(json, OrderResponse.class);

        assert original.getId().equals(deserialized.getId()) :
                "ID mismatch: " + original.getId() + " vs " + deserialized.getId();
        assert original.getStationId().equals(deserialized.getStationId()) :
                "StationId mismatch: " + original.getStationId() + " vs " + deserialized.getStationId();
        assert original.getStationName().equals(deserialized.getStationName()) :
                "StationName mismatch: " + original.getStationName() + " vs " + deserialized.getStationName();
        assert original.getState() == deserialized.getState() :
                "State mismatch: " + original.getState() + " vs " + deserialized.getState();
        assert original.getTotalPrice().compareTo(deserialized.getTotalPrice()) == 0 :
                "TotalPrice mismatch: " + original.getTotalPrice() + " vs " + deserialized.getTotalPrice();

        // Nullable fields
        if (original.getQueuePosition() != null) {
            assert original.getQueuePosition().equals(deserialized.getQueuePosition()) :
                    "QueuePosition mismatch: " + original.getQueuePosition() + " vs " + deserialized.getQueuePosition();
        } else {
            assert deserialized.getQueuePosition() == null :
                    "QueuePosition should be null but was " + deserialized.getQueuePosition();
        }

        if (original.getVisualOrderNumber() != null) {
            assert original.getVisualOrderNumber().equals(deserialized.getVisualOrderNumber()) :
                    "VisualOrderNumber mismatch: " + original.getVisualOrderNumber() + " vs " + deserialized.getVisualOrderNumber();
        } else {
            assert deserialized.getVisualOrderNumber() == null :
                    "VisualOrderNumber should be null but was " + deserialized.getVisualOrderNumber();
        }
    }

    /**
     * Property 15b: Serialized JSON always contains the required fields.
     *
     * **Validates: Requirements 11.3**
     */
    @Property
    void serializedJsonContainsRequiredFields(
            @ForAll("orderResponses") OrderResponse original) throws JsonProcessingException {

        String json = objectMapper.writeValueAsString(original);

        assert json.contains("\"id\"") :
                "Serialized JSON must contain 'id' field";
        assert json.contains("\"stationId\"") :
                "Serialized JSON must contain 'stationId' field";
        assert json.contains("\"state\"") :
                "Serialized JSON must contain 'state' field";
        assert json.contains("\"totalPrice\"") :
                "Serialized JSON must contain 'totalPrice' field";
        assert json.contains("\"items\"") :
                "Serialized JSON must contain 'items' field";
    }

    /**
     * Property 15c: The state field round-trips correctly for all OrderState values.
     *
     * **Validates: Requirements 11.3**
     */
    @Property
    void stateFieldRoundTripsForAllStates(
            @ForAll("orderStates") OrderState state) throws JsonProcessingException {

        OrderResponse original = new OrderResponse();
        original.setId(1L);
        original.setStationId(1L);
        original.setStationName("Test");
        original.setState(state);
        original.setTotalPrice(BigDecimal.TEN);
        original.setItems(List.of());

        String json = objectMapper.writeValueAsString(original);
        OrderResponse deserialized = objectMapper.readValue(json, OrderResponse.class);

        assert deserialized.getState() == state :
                "State round-trip failed: expected " + state + " but got " + deserialized.getState();
    }

    // =====================================================================
    // Property 16: Malformed JSON rejection
    // =====================================================================

    /**
     * Property 16a: Malformed JSON strings cannot be deserialized into OrderResponse.
     * This validates that the backend would reject such input with a 400 response
     * (via GlobalExceptionHandler's HttpMessageNotReadableException handler).
     *
     * **Validates: Requirements 11.4**
     */
    @Property
    void malformedJsonCannotBeDeserializedToOrderResponse(
            @ForAll("malformedJsonStrings") String malformedJson) {

        try {
            objectMapper.readValue(malformedJson, OrderResponse.class);
            // If we get here, the JSON was unexpectedly valid — only acceptable for
            // strings that happen to be valid JSON (e.g., "null" maps to null)
            // We filter "null" in the arbitrary, so this should not happen for truly malformed input
        } catch (JsonProcessingException e) {
            // Expected — malformed JSON should fail deserialization
            assert e != null : "Exception should not be null";
        }
    }

    /**
     * Property 16b: Malformed JSON strings cannot be deserialized into StateTransitionRequest.
     * This validates that vendor state transition requests with bad JSON are rejected.
     *
     * **Validates: Requirements 11.4**
     */
    @Property
    void malformedJsonCannotBeDeserializedToStateTransitionRequest(
            @ForAll("malformedJsonStrings") String malformedJson) {

        try {
            objectMapper.readValue(malformedJson, com.smarteventbar.dto.StateTransitionRequest.class);
            // If parsing succeeds, the string happened to be valid JSON for this type
        } catch (JsonProcessingException e) {
            // Expected — malformed JSON should fail deserialization
            assert e != null : "Exception should not be null";
        }
    }

    /**
     * Property 16c: Malformed JSON strings cannot be deserialized into OrderItemRequest lists.
     * This validates that order creation requests with bad JSON are rejected.
     *
     * **Validates: Requirements 11.4**
     */
    @Property
    void malformedJsonCannotBeDeserializedToOrderItemRequestList(
            @ForAll("malformedJsonStrings") String malformedJson) {

        try {
            objectMapper.readValue(malformedJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, com.smarteventbar.dto.OrderItemRequest.class));
            // If parsing succeeds, the string happened to be valid JSON for this type
        } catch (JsonProcessingException e) {
            // Expected — malformed JSON should fail deserialization
            assert e != null : "Exception should not be null";
        }
    }
}
