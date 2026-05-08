package com.smarteventbar.controller;

import com.smarteventbar.dto.AuthResponse;
import com.smarteventbar.dto.VendorLoginRequest;
import com.smarteventbar.exception.RateLimitExceededException;
import com.smarteventbar.repository.StationRepository;
import com.smarteventbar.service.AuthService;
import com.smarteventbar.service.impl.RateLimitServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/vendor")
public class AuthController {

    private final AuthService authService;
    private final RateLimitServiceImpl rateLimitService;
    private final StationRepository stationRepository;

    public AuthController(AuthService authService, RateLimitServiceImpl rateLimitService,
                          StationRepository stationRepository) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.stationRepository = stationRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody VendorLoginRequest request) {
        if (!rateLimitService.isAuthAttemptAllowed(request.getStationId())) {
            long retryAfter = rateLimitService.getAuthRetryAfterSeconds(request.getStationId());
            throw new RateLimitExceededException("Too many authentication attempts. Try again later.", retryAfter);
        }

        try {
            AuthResponse response = authService.authenticate(request.getStationId(), request.getAccessCode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            rateLimitService.recordFailedAuthAttempt(request.getStationId());
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();
        if (!authService.validateToken(token)) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        Long stationId = authService.getStationIdFromToken(token);
        // For MVP, re-issue a fresh JWT based on the valid token's station ID
        // The authenticate method validates the access code, so we look up the station directly
        com.smarteventbar.model.entity.Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Station not found"));
        AuthResponse response = authService.authenticate(stationId, station.getAccessCode());
        return ResponseEntity.ok(response);
    }
}
