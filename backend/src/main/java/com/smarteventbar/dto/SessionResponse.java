package com.smarteventbar.dto;

import com.smarteventbar.model.entity.CustomerSession;

import java.time.LocalDateTime;

public class SessionResponse {

    private String sessionId;
    private Long stationId;
    private String stationName;
    private LocalDateTime lastActivityAt;
    private boolean expired;
    private LocalDateTime createdAt;

    public SessionResponse() {
    }

    public static SessionResponse fromEntity(CustomerSession session) {
        SessionResponse r = new SessionResponse();
        r.setSessionId(session.getSessionId());
        r.setStationId(session.getStation().getId());
        r.setStationName(session.getStation().getName());
        r.setLastActivityAt(session.getLastActivityAt());
        r.setExpired(session.isExpired());
        r.setCreatedAt(session.getCreatedAt());
        return r;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
