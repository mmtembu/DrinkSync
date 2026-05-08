package com.smarteventbar.service;

import com.smarteventbar.exception.InvalidStateTransitionException;
import com.smarteventbar.model.enums.OrderState;

import java.util.List;

public interface OrderStateMachine {

    OrderState validateTransition(OrderState current, OrderState target) throws InvalidStateTransitionException;

    boolean isValidTransition(OrderState current, OrderState target);

    List<OrderState> getAllowedTransitions(OrderState current);

    boolean isTerminalState(OrderState state);
}
