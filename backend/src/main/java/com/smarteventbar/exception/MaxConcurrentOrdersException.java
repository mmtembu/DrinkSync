package com.smarteventbar.exception;

public class MaxConcurrentOrdersException extends RuntimeException {

    private final int activeCount;

    public MaxConcurrentOrdersException(int activeCount) {
        super("Maximum 3 active orders per session");
        this.activeCount = activeCount;
    }

    public int getActiveCount() {
        return activeCount;
    }
}
