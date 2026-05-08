package com.smarteventbar.service.impl;

import com.smarteventbar.exception.InvalidStateTransitionException;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.service.OrderStateMachine;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStateMachineImpl implements OrderStateMachine {

    private static final Map<OrderState, Set<OrderState>> TRANSITIONS;

    static {
        Map<OrderState, Set<OrderState>> map = new EnumMap<>(OrderState.class);
        map.put(OrderState.DRAFT, EnumSet.of(OrderState.AWAITING_PAYMENT, OrderState.CANCELLED));
        map.put(OrderState.AWAITING_PAYMENT, EnumSet.of(OrderState.PAID, OrderState.CANCELLED));
        map.put(OrderState.PAID, EnumSet.of(OrderState.PREPARING));
        map.put(OrderState.PREPARING, EnumSet.of(OrderState.READY));
        map.put(OrderState.READY, EnumSet.of(OrderState.COLLECTED, OrderState.EXPIRED));
        map.put(OrderState.COLLECTED, EnumSet.noneOf(OrderState.class));
        map.put(OrderState.CANCELLED, EnumSet.noneOf(OrderState.class));
        map.put(OrderState.EXPIRED, EnumSet.noneOf(OrderState.class));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    @Override
    public OrderState validateTransition(OrderState current, OrderState target) throws InvalidStateTransitionException {
        if (current.isTerminal()) {
            throw new InvalidStateTransitionException(current, target);
        }

        Set<OrderState> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new InvalidStateTransitionException(current, target);
        }

        return target;
    }

    @Override
    public boolean isValidTransition(OrderState current, OrderState target) {
        if (current.isTerminal()) {
            return false;
        }

        Set<OrderState> allowed = TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }

    @Override
    public List<OrderState> getAllowedTransitions(OrderState current) {
        Set<OrderState> allowed = TRANSITIONS.get(current);
        if (allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        return List.copyOf(allowed);
    }

    @Override
    public boolean isTerminalState(OrderState state) {
        return state.isTerminal();
    }
}
