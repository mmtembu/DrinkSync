package com.smarteventbar.service;

import com.smarteventbar.dto.AuthResponse;

public interface AuthService {

    AuthResponse authenticate(Long stationId, String accessCode);

    boolean validateToken(String token);

    Long getStationIdFromToken(String token);

    String generateAccessCode();
}
