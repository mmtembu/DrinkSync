package com.smarteventbar.dto;

import java.time.LocalDateTime;

public class AuthResponse {

    private String token;
    private LocalDateTime expiresAt;
    private Long stationId;

    public AuthResponse(String token, LocalDateTime expiresAt, Long stationId) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.stationId = stationId;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public Long getStationId() {
        return stationId;
    }
}
