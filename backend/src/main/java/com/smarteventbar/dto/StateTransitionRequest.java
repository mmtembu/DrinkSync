package com.smarteventbar.dto;

import com.smarteventbar.model.enums.OrderState;
import jakarta.validation.constraints.NotNull;

public class StateTransitionRequest {

    @NotNull
    private OrderState targetState;

    public StateTransitionRequest() {
    }

    public OrderState getTargetState() { return targetState; }
    public void setTargetState(OrderState targetState) { this.targetState = targetState; }
}
