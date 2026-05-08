package com.smarteventbar.service.impl;

import com.smarteventbar.exception.InvalidStateTransitionException;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.service.OrderStateMachine;
import net.jqwik.api.*;
import net.jqwik.api.constraints.Size;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Property-based tests for OrderStateMachine.
 *
 * Property 5: Order state machine enforcement
 * - Generate random (currentState, targetState) pairs from all 8 OrderState values
 * - Verify only valid transitions succeed, terminal states reject all, invalid transitions throw exception
 *
 * Validates: Requirements 4.1, 4.3, 4.7, 8.1, 8.2, 8.3, 8.4, 13.1, 13.2, 13.3, 15.2
 */
class OrderStateMachineImplProperties {

    private final OrderStateMachine stateMachine = new OrderStateMachineImpl();

    // Define the complete set of valid transitions per the design document
    private static final Set<OrderState> TERMINAL_STATES = EnumSet.of(
            OrderState.COLLECTED, OrderState.CANCELLED, OrderState.EXPIRED
    );

    private boolean isValidTransitionPair(OrderState from, OrderState to) {
        return switch (from) {
            case DRAFT -> to == OrderState.AWAITING_PAYMENT || to == OrderState.CANCELLED;
            case AWAITING_PAYMENT -> to == OrderState.PAID || to == OrderState.CANCELLED;
            case PAID -> to == OrderState.PREPARING;
            case PREPARING -> to == OrderState.READY;
            case READY -> to == OrderState.COLLECTED || to == OrderState.EXPIRED;
            case COLLECTED, CANCELLED, EXPIRED -> false;
        };
    }

    @Provide
    Arbitrary<OrderState> orderStates() {
        return Arbitraries.of(OrderState.values());
    }

    @Provide
    Arbitrary<Tuple.Tuple2<OrderState, OrderState>> statePairs() {
        return Combinators.combine(orderStates(), orderStates()).as(Tuple::of);
    }

    // --- Property 5a: Valid transitions succeed and return the target state ---

    @Property
    void validTransitionsSucceedAndReturnTargetState(
            @ForAll("statePairs") Tuple.Tuple2<OrderState, OrderState> pair) {
        OrderState current = pair.get1();
        OrderState target = pair.get2();

        if (isValidTransitionPair(current, target)) {
            OrderState result = stateMachine.validateTransition(current, target);
            assert result == target :
                    "Expected validateTransition(" + current + ", " + target + ") to return " + target + " but got " + result;
        }
    }

    // --- Property 5b: Invalid transitions throw InvalidStateTransitionException ---

    @Property
    void invalidTransitionsThrowException(
            @ForAll("statePairs") Tuple.Tuple2<OrderState, OrderState> pair) {
        OrderState current = pair.get1();
        OrderState target = pair.get2();

        if (!isValidTransitionPair(current, target)) {
            try {
                stateMachine.validateTransition(current, target);
                assert false :
                        "Expected InvalidStateTransitionException for transition " + current + " → " + target;
            } catch (InvalidStateTransitionException e) {
                assert e.getCurrentState() == current :
                        "Exception currentState should be " + current + " but was " + e.getCurrentState();
                assert e.getTargetState() == target :
                        "Exception targetState should be " + target + " but was " + e.getTargetState();
            }
        }
    }

    // --- Property 5c: Terminal states reject ALL transitions ---

    @Property
    void terminalStatesRejectAllTransitions(
            @ForAll("orderStates") OrderState target) {
        for (OrderState terminal : TERMINAL_STATES) {
            try {
                stateMachine.validateTransition(terminal, target);
                assert false :
                        "Expected InvalidStateTransitionException from terminal state " + terminal + " to " + target;
            } catch (InvalidStateTransitionException e) {
                // Expected — terminal states must reject all transitions
                assert e.getCurrentState() == terminal;
                assert e.getTargetState() == target;
            }
        }
    }

    // --- Property 5d: isValidTransition is consistent with validateTransition ---

    @Property
    void isValidTransitionConsistentWithValidateTransition(
            @ForAll("statePairs") Tuple.Tuple2<OrderState, OrderState> pair) {
        OrderState current = pair.get1();
        OrderState target = pair.get2();

        boolean isValid = stateMachine.isValidTransition(current, target);

        if (isValid) {
            // Should not throw
            OrderState result = stateMachine.validateTransition(current, target);
            assert result == target :
                    "isValidTransition returned true but validateTransition did not return target state";
        } else {
            // Should throw
            try {
                stateMachine.validateTransition(current, target);
                assert false :
                        "isValidTransition returned false but validateTransition did not throw for " + current + " → " + target;
            } catch (InvalidStateTransitionException e) {
                // Expected
            }
        }
    }

    // --- Property 5e: isTerminalState correctly identifies terminal states ---

    @Property
    void isTerminalStateCorrectlyIdentifiesTerminalStates(
            @ForAll("orderStates") OrderState state) {
        boolean expected = TERMINAL_STATES.contains(state);
        boolean actual = stateMachine.isTerminalState(state);
        assert expected == actual :
                "isTerminalState(" + state + ") returned " + actual + " but expected " + expected;
    }

    // --- Property 5f: getAllowedTransitions returns only valid targets ---

    @Property
    void getAllowedTransitionsReturnsOnlyValidTargets(
            @ForAll("orderStates") OrderState current) {
        List<OrderState> allowed = stateMachine.getAllowedTransitions(current);

        // Every state in the allowed list must be a valid transition
        for (OrderState target : allowed) {
            assert isValidTransitionPair(current, target) :
                    "getAllowedTransitions(" + current + ") includes " + target + " which is not a valid transition";
            assert stateMachine.isValidTransition(current, target) :
                    "getAllowedTransitions includes " + target + " but isValidTransition returns false";
        }

        // Every valid transition must be in the allowed list
        for (OrderState target : OrderState.values()) {
            if (isValidTransitionPair(current, target)) {
                assert allowed.contains(target) :
                        "Valid transition " + current + " → " + target + " is missing from getAllowedTransitions";
            }
        }
    }

    // --- Property 5g: Terminal states have empty allowed transitions ---

    @Property
    void terminalStatesHaveNoAllowedTransitions(
            @ForAll("orderStates") OrderState state) {
        if (TERMINAL_STATES.contains(state)) {
            List<OrderState> allowed = stateMachine.getAllowedTransitions(state);
            assert allowed.isEmpty() :
                    "Terminal state " + state + " should have no allowed transitions but has: " + allowed;
        }
    }

    // --- Property 5h: Sequences of valid transitions never reach a non-terminal state from a terminal state ---

    @Property
    void sequentialValidTransitionsRespectTerminality(
            @ForAll("orderStates") OrderState start,
            @ForAll @Size(min = 1, max = 10) List<@From("orderStates") OrderState> targets) {
        OrderState current = start;

        for (OrderState target : targets) {
            if (stateMachine.isTerminalState(current)) {
                // Once terminal, all further transitions must fail
                assert !stateMachine.isValidTransition(current, target) :
                        "Terminal state " + current + " should not allow transition to " + target;
                break; // No point continuing — state is stuck
            }

            if (stateMachine.isValidTransition(current, target)) {
                OrderState result = stateMachine.validateTransition(current, target);
                assert result == target;
                current = result;
            }
        }
    }
}
