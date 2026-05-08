package com.smarteventbar.model.enums;

public enum OrderState {
    DRAFT,
    AWAITING_PAYMENT,
    PAID,
    PREPARING,
    READY,
    COLLECTED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COLLECTED || this == CANCELLED || this == EXPIRED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
