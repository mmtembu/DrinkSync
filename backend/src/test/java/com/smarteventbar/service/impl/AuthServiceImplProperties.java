package com.smarteventbar.service.impl;

import com.smarteventbar.dto.AuthResponse;
import com.smarteventbar.exception.InvalidAccessCodeException;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.StationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.NumericChars;
import net.jqwik.api.constraints.StringLength;
import org.mockito.Mockito;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.when;

/**
 * Property-based tests for AuthService (authentication and JWT).
 *
 * Property 17: Vendor endpoint authentication enforcement
 * - Generate random requests to vendor endpoints with valid/invalid/missing/expired JWT tokens;
 *   verify 401 for invalid, success for valid
 *
 * Property 24: Vendor authentication — valid access code produces JWT
 * - Generate random 6-digit alphanumeric codes; verify correct code → JWT with 12h expiry, incorrect → error
 *
 * Validates: Requirements 12.1, 12.2, 12.5, 12.6, 17.1, 17.5
 */
class AuthServiceImplProperties {

    private static final String JWT_SECRET = "smart-event-bar-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256";
    private static final int EXPIRATION_HOURS = 12;
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    private final StationRepository stationRepository = Mockito.mock(StationRepository.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(stationRepository, JWT_SECRET, EXPIRATION_HOURS);

    // --- Arbitraries ---

    @Provide
    Arbitrary<String> validAccessCodes() {
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofLength(6);
    }

    @Provide
    Arbitrary<String> invalidAccessCodes() {
        // Codes that differ from the station's code: wrong length, wrong chars, or just different
        return Arbitraries.oneOf(
                // Wrong length (1-5 or 7-12 chars)
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .ofMinLength(1).ofMaxLength(5),
                Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .withCharRange('0', '9')
                        .ofMinLength(7).ofMaxLength(12),
                // Correct length but with lowercase (invalid chars)
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofLength(6),
                // Empty string
                Arbitraries.just("")
        );
    }

    @Provide
    Arbitrary<Long> stationIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<String> randomStrings() {
        return Arbitraries.strings().ofMinLength(0).ofMaxLength(200);
    }

    // --- Property 24a: Valid access code produces a JWT with correct station ID ---

    @Property
    void validAccessCodeProducesJwtWithCorrectStationId(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        AuthResponse response = authService.authenticate(stationId, accessCode);

        assert response != null : "AuthResponse should not be null for valid access code";
        assert response.getToken() != null && !response.getToken().isEmpty() :
                "Token should not be null or empty";
        assert response.getStationId().equals(stationId) :
                "Response stationId should match input stationId";
    }

    // --- Property 24b: Valid access code produces JWT with approximately 12-hour expiry ---

    @Property
    void validAccessCodeProducesJwtWith12HourExpiry(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        LocalDateTime before = LocalDateTime.now().plusHours(EXPIRATION_HOURS).minusSeconds(5);
        AuthResponse response = authService.authenticate(stationId, accessCode);
        LocalDateTime after = LocalDateTime.now().plusHours(EXPIRATION_HOURS).plusSeconds(5);

        assert response.getExpiresAt() != null : "ExpiresAt should not be null";
        assert response.getExpiresAt().isAfter(before) :
                "ExpiresAt should be after (now + 12h - 5s), got " + response.getExpiresAt();
        assert response.getExpiresAt().isBefore(after) :
                "ExpiresAt should be before (now + 12h + 5s), got " + response.getExpiresAt();
    }

    // --- Property 24c: Invalid access code throws InvalidAccessCodeException ---

    @Property
    void invalidAccessCodeThrowsException(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String correctCode,
            @ForAll("validAccessCodes") String attemptedCode) {

        Assume.that(!correctCode.equals(attemptedCode));

        Station station = createStation(stationId, correctCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        try {
            authService.authenticate(stationId, attemptedCode);
            assert false : "Expected InvalidAccessCodeException for incorrect code '" + attemptedCode
                    + "' (correct: '" + correctCode + "')";
        } catch (InvalidAccessCodeException e) {
            // Expected — incorrect access code must be rejected
        }
    }

    // --- Property 24d: Non-existent station throws InvalidAccessCodeException ---

    @Property
    void nonExistentStationThrowsException(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        when(stationRepository.findById(stationId)).thenReturn(Optional.empty());

        try {
            authService.authenticate(stationId, accessCode);
            assert false : "Expected InvalidAccessCodeException for non-existent station " + stationId;
        } catch (InvalidAccessCodeException e) {
            // Expected — non-existent station must be rejected
        }
    }

    // --- Property 17a: Valid JWT token is accepted by validateToken ---

    @Property
    void validJwtTokenIsAccepted(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        AuthResponse response = authService.authenticate(stationId, accessCode);

        assert authService.validateToken(response.getToken()) :
                "A freshly generated token should be valid";
    }

    // --- Property 17b: Random strings are rejected as invalid tokens ---

    @Property
    void randomStringsAreRejectedAsTokens(@ForAll("randomStrings") String randomToken) {
        // Random strings should never validate as a proper JWT
        assert !authService.validateToken(randomToken) :
                "Random string should not be accepted as a valid token: " + randomToken;
    }

    // --- Property 17c: Expired JWT tokens are rejected ---

    @Property
    void expiredJwtTokensAreRejected(@ForAll("stationIds") Long stationId) {
        // Manually create an expired token (expired 1 hour ago)
        String expiredToken = Jwts.builder()
                .subject(stationId.toString())
                .issuedAt(new Date(System.currentTimeMillis() - 86400000)) // 24h ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1h ago
                .signWith(SIGNING_KEY)
                .compact();

        assert !authService.validateToken(expiredToken) :
                "Expired token should not be accepted as valid";
    }

    // --- Property 17d: Tokens signed with a different key are rejected ---

    @Property
    void tokensSignedWithDifferentKeyAreRejected(@ForAll("stationIds") Long stationId) {
        SecretKey differentKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-that-is-also-at-least-256-bits-long-for-testing"
                        .getBytes(StandardCharsets.UTF_8));

        String foreignToken = Jwts.builder()
                .subject(stationId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 43200000)) // 12h from now
                .signWith(differentKey)
                .compact();

        assert !authService.validateToken(foreignToken) :
                "Token signed with a different key should not be accepted";
    }

    // --- Property 17e: getStationIdFromToken returns the correct station ID for valid tokens ---

    @Property
    void getStationIdFromTokenReturnsCorrectId(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        AuthResponse response = authService.authenticate(stationId, accessCode);
        Long extractedId = authService.getStationIdFromToken(response.getToken());

        assert extractedId.equals(stationId) :
                "Extracted station ID " + extractedId + " should match original " + stationId;
    }

    // --- Property 17f: Tampered tokens are rejected (payload modification) ---

    @Property
    void tamperedTokensAreRejected(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        AuthResponse response = authService.authenticate(stationId, accessCode);
        String validToken = response.getToken();

        // Split the JWT into header.payload.signature parts
        String[] parts = validToken.split("\\.");
        assert parts.length == 3 : "JWT should have 3 parts";

        // Tamper with the payload by replacing it with a different base64-encoded payload
        // This simulates someone modifying the claims (e.g., changing station ID)
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"9999\",\"iat\":1700000000,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assert !authService.validateToken(tamperedToken) :
                "Token with tampered payload should not be accepted as valid";
    }

    // --- Property 24e: Generated access codes are always 6 uppercase alphanumeric characters ---

    @Property(tries = 100)
    void generatedAccessCodesAreValid() {
        String code = authService.generateAccessCode();

        assert code.length() == 6 :
                "Access code length should be 6 but was " + code.length();
        assert code.matches("^[A-Z0-9]{6}$") :
                "Access code should contain only uppercase letters and digits: " + code;
    }

    // --- Property 24f: Same access code authenticates consistently (idempotent) ---

    @Property
    void sameAccessCodeAuthenticatesConsistently(
            @ForAll("stationIds") Long stationId,
            @ForAll("validAccessCodes") String accessCode) {

        Station station = createStation(stationId, accessCode);
        when(stationRepository.findById(stationId)).thenReturn(Optional.of(station));

        AuthResponse response1 = authService.authenticate(stationId, accessCode);
        AuthResponse response2 = authService.authenticate(stationId, accessCode);

        // Both should succeed and produce valid tokens (though tokens themselves will differ)
        assert response1.getStationId().equals(stationId);
        assert response2.getStationId().equals(stationId);
        assert authService.validateToken(response1.getToken());
        assert authService.validateToken(response2.getToken());
    }

    // --- Helper methods ---

    private Station createStation(Long id, String accessCode) {
        Station station = new Station("Station " + id, "Location " + id, new BigDecimal("5.00"), accessCode, 10);
        station.setId(id);
        return station;
    }
}
