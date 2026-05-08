package com.smarteventbar.service;

import com.smarteventbar.model.entity.CustomerSession;

public interface SessionService {

    CustomerSession createSession(Long stationId);

    CustomerSession getSession(String sessionId);

    void validateSessionForStation(String sessionId, Long stationId);

    int getActiveOrderCount(String sessionId);

    boolean isSessionExpired(String sessionId);

    void touchSession(String sessionId);

    void expireInactiveSessions();
}
