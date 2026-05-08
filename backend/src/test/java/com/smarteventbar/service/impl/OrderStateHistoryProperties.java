package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.OrderStateHistory;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.model.enums.TransitionTrigger;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Property-based tests for order state history timestamp recording.
 *
 * Property 13: State transition timestamps are recorded
 * - Generate random state transitions; verify ORDER_STATE_HISTORY entry with non-null timestamp
 *
 * Validates: Requirements 10.2
 */
class OrderStateHistoryProperties {

    private static final Map<OrderState, Set<OrderState>> VALID_TRANSITIONS = Map.of(
            OrderState.DRAFT, EnumSet.of(OrderState.AWAITING_PAYMENT, OrderState.CANCELLED),
            OrderState.AWAITING_PAYMENT, EnumSet.of(OrderState.PAID, OrderState.CANCELLED),
            OrderState.PAID, EnumSet.of(OrderState.PREPARING),
            OrderState.PREPARING, EnumSet.of(OrderState.READY),
            OrderState.READY, EnumSet.of(OrderState.COLLECTED, OrderState.EXPIRED)
    );

    @Provide
    Arbitrary<OrderState> orderStates() {
        return Arbitraries.of(OrderState.values());
    }

    @Provide
    Arbitrary<TransitionTrigger> triggers() {
        return Arbitraries.of(TransitionTrigger.values());
    }

    @Provide
    Arbitrary<Tuple.Tuple2<OrderState, OrderState>> validTransitionPairs() {
        return Arbitraries.of(OrderState.values())
                .filter(from -> VALID_TRANSITIONS.containsKey(from))
                .flatMap(from -> Arbitraries.of(VALID_TRANSITIONS.get(from).toArray(new OrderState[0]))
                        .map(to -> Tuple.of(from, to)));
    }

    // --- Property 13a: Every state transition records a non-null timestamp ---

    @Property
    void stateTransitionAlwaysRecordsNonNullTimestamp(
            @ForAll("validTransitionPairs") Tuple.Tuple2<OrderState, OrderState> pair,
            @ForAll("triggers") TransitionTrigger trigger) {

        CustomerOrder order = createTestOrder();
        OrderState fromState = pair.get1();
        OrderState toState = pair.get2();

        OrderStateHistory history = new OrderStateHistory(order, fromState, toState, trigger);

        assert history.getTransitionedAt() != null :
                "OrderStateHistory transitionedAt must not be null for transition " +
                fromState + " → " + toState + " triggered by " + trigger;
    }

    // --- Property 13b: Recorded timestamp is not in the future ---

    @Property
    void stateTransitionTimestampIsNotInTheFuture(
            @ForAll("validTransitionPairs") Tuple.Tuple2<OrderState, OrderState> pair,
            @ForAll("triggers") TransitionTrigger trigger) {

        LocalDateTime beforeCreation = LocalDateTime.now();

        CustomerOrder order = createTestOrder();
        OrderState fromState = pair.get1();
        OrderState toState = pair.get2();

        OrderStateHistory history = new OrderStateHistory(order, fromState, toState, trigger);

        LocalDateTime afterCreation = LocalDateTime.now();

        assert !history.getTransitionedAt().isBefore(beforeCreation) :
                "Timestamp should not be before the creation time";
        assert !history.getTransitionedAt().isAfter(afterCreation) :
                "Timestamp should not be after the current time";
    }

    // --- Property 13c: State history correctly records from and to states ---

    @Property
    void stateHistoryRecordsCorrectFromAndToStates(
            @ForAll("validTransitionPairs") Tuple.Tuple2<OrderState, OrderState> pair,
            @ForAll("triggers") TransitionTrigger trigger) {

        CustomerOrder order = createTestOrder();
        OrderState fromState = pair.get1();
        OrderState toState = pair.get2();

        OrderStateHistory history = new OrderStateHistory(order, fromState, toState, trigger);

        assert history.getFromState() == fromState :
                "History fromState should be " + fromState + " but was " + history.getFromState();
        assert history.getToState() == toState :
                "History toState should be " + toState + " but was " + history.getToState();
    }

    // --- Property 13d: Initial order creation (null → DRAFT) records a non-null timestamp ---

    @Property
    void initialOrderCreationRecordsTimestamp(
            @ForAll("triggers") TransitionTrigger trigger) {

        CustomerOrder order = createTestOrder();

        OrderStateHistory history = new OrderStateHistory(order, null, OrderState.DRAFT, trigger);

        assert history.getTransitionedAt() != null :
                "Initial creation (null → DRAFT) must have a non-null timestamp";
        assert history.getFromState() == null :
                "Initial creation should have null fromState";
        assert history.getToState() == OrderState.DRAFT :
                "Initial creation should have DRAFT as toState";
    }

    // --- Property 13e: State history is associated with the correct order ---

    @Property
    void stateHistoryIsAssociatedWithCorrectOrder(
            @ForAll("validTransitionPairs") Tuple.Tuple2<OrderState, OrderState> pair,
            @ForAll("triggers") TransitionTrigger trigger) {

        CustomerOrder order = createTestOrder();
        OrderState fromState = pair.get1();
        OrderState toState = pair.get2();

        OrderStateHistory history = new OrderStateHistory(order, fromState, toState, trigger);

        assert history.getOrder() == order :
                "History entry must be associated with the order it was created for";
    }

    // --- Property 13f: Trigger type is correctly recorded ---

    @Property
    void triggerTypeIsCorrectlyRecorded(
            @ForAll("validTransitionPairs") Tuple.Tuple2<OrderState, OrderState> pair,
            @ForAll("triggers") TransitionTrigger trigger) {

        CustomerOrder order = createTestOrder();
        OrderState fromState = pair.get1();
        OrderState toState = pair.get2();

        OrderStateHistory history = new OrderStateHistory(order, fromState, toState, trigger);

        assert history.getTriggeredBy() == trigger :
                "History triggeredBy should be " + trigger + " but was " + history.getTriggeredBy();
    }

    private CustomerOrder createTestOrder() {
        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setState(OrderState.DRAFT);
        order.setTotalPrice(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
