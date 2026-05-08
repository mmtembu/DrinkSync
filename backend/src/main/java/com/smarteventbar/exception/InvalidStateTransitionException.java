package com.smarteventbar.exception;

import com.smarteventbar.model.enums.OrderState;

public class InvalidStateTransitionException extends RuntimeException {

    private final OrderState currentState;
    private final OrderState targetState;

    public InvalidStateTransitionException(OrderState currentState, OrderState targetState) {
        super("Invalid state transition from " + currentState + " to " + targetState);
        this.currentState = currentState;
        this.targetState = targetState;
    }

    public OrderState getCurrentState() {
        return currentState;
    }

    public OrderState getTargetState() {
        return targetState;
    }
}
