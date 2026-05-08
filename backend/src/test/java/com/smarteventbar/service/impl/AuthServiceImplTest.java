package com.smarteventbar.service.impl;

import com.smarteventbar.dto.AuthResponse;
import com.smarteventbar.exception.InvalidAccessCodeException;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String JWT_SECRET = "smart-event-bar-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256";
    private static final int EXPIRATION_HOURS = 12;

    @Mock
    private StationRepository stationRepository;

    private AuthServiceImpl authService;

    private Station testStation;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(stationRepository, JWT_SECRET, EXPIRATION_HOURS);
        testStation = new Station("Test Station", "Test Location", new BigDecimal("10.00"), "ABC123", 10);
        testStation.setId(1L);
    }

    // --- authenticate tests ---

    @Test
    void authenticate_validAccessCode_returnsAuthResponse() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = authService.authenticate(1L, "ABC123");

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertFalse(response.getToken().isEmpty());
        assertEquals(1L, response.getStationId());
        assertNotNull(response.getExpiresAt());
        assertTrue(response.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(response.getExpiresAt().isBefore(LocalDateTime.now().plusHours(13)));
    }

    @Test
    void authenticate_invalidAccessCode_throwsInvalidAccessCodeException() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        assertThrows(InvalidAccessCodeException.class,
                () -> authService.authenticate(1L, "WRONG1"));
    }

    @Test
    void authenticate_stationNotFound_throwsInvalidAccessCodeException() {
        when(stationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(InvalidAccessCodeException.class,
                () -> authService.authenticate(999L, "ABC123"));
    }

    @Test
    void authenticate_tokenContainsStationId() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = authService.authenticate(1L, "ABC123");
        Long extractedStationId = authService.getStationIdFromToken(response.getToken());

        assertEquals(1L, extractedStationId);
    }

    // --- validateToken tests ---

    @Test
    void validateToken_validToken_returnsTrue() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = authService.authenticate(1L, "ABC123");

        assertTrue(authService.validateToken(response.getToken()));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(authService.validateToken("invalid.token.here"));
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        assertFalse(authService.validateToken(null));
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertFalse(authService.validateToken(""));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = authService.authenticate(1L, "ABC123");
        String tamperedToken = response.getToken() + "tampered";

        assertFalse(authService.validateToken(tamperedToken));
    }

    @Test
    void validateToken_tokenSignedWithDifferentKey_returnsFalse() {
        // Create a second AuthService with a different secret
        AuthServiceImpl otherAuthService = new AuthServiceImpl(
                stationRepository,
                "a-completely-different-secret-key-that-is-also-at-least-256-bits-long",
                EXPIRATION_HOURS);

        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = otherAuthService.authenticate(1L, "ABC123");

        assertFalse(authService.validateToken(response.getToken()));
    }

    // --- getStationIdFromToken tests ---

    @Test
    void getStationIdFromToken_validToken_returnsStationId() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        AuthResponse response = authService.authenticate(1L, "ABC123");
        Long stationId = authService.getStationIdFromToken(response.getToken());

        assertEquals(1L, stationId);
    }

    @Test
    void getStationIdFromToken_invalidToken_throwsException() {
        assertThrows(Exception.class,
                () -> authService.getStationIdFromToken("invalid.token.here"));
    }

    // --- generateAccessCode tests ---

    @Test
    void generateAccessCode_returns6CharacterString() {
        String code = authService.generateAccessCode();

        assertEquals(6, code.length());
    }

    @Test
    void generateAccessCode_containsOnlyUppercaseAlphanumeric() {
        String code = authService.generateAccessCode();

        assertTrue(code.matches("^[A-Z0-9]{6}$"),
                "Access code should contain only uppercase letters and digits: " + code);
    }

    @Test
    void generateAccessCode_generatesUniqueValues() {
        String code1 = authService.generateAccessCode();
        String code2 = authService.generateAccessCode();

        // While not guaranteed, two random 6-char codes should almost never collide
        // This test verifies the method produces different outputs across calls
        assertNotEquals(code1, code2,
                "Two consecutive access codes should be different (extremely unlikely to collide)");
    }

    @Test
    void authenticate_expiresAtIsApproximately12HoursFromNow() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(testStation));

        LocalDateTime before = LocalDateTime.now().plusHours(EXPIRATION_HOURS).minusSeconds(5);
        AuthResponse response = authService.authenticate(1L, "ABC123");
        LocalDateTime after = LocalDateTime.now().plusHours(EXPIRATION_HOURS).plusSeconds(5);

        assertTrue(response.getExpiresAt().isAfter(before));
        assertTrue(response.getExpiresAt().isBefore(after));
    }
}
