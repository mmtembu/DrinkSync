package com.smarteventbar.exception;

public class StationLockException extends RuntimeException {

    private final Long lockedStationId;

    public StationLockException(Long lockedStationId) {
        super("Active orders exist at another station");
        this.lockedStationId = lockedStationId;
    }

    public Long getLockedStationId() {
        return lockedStationId;
    }
}
