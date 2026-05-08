package com.smarteventbar.config;

import com.smarteventbar.service.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for JwtAuthFilter.
 *
 * Property 17: Vendor endpoint authentication enforcement
 * - Generate random requests to vendor endpoints with valid/invalid/missing/expired JWT tokens;
 *   verify 401 for invalid, success for valid
 * - Customer endpoints bypass the filter entirely regardless of token presence
 *
 * Validates: Requirements 12.1, 12.2, 12.5, 12.6
 */
class JwtAuthFilterProperties {

    private static final String JWT_SECRET = "smart-event-bar-jwt-secret-key-that-is-at-least-256-bits-long-for-hs256";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    // Vendor-protected endpoint patterns for testing
    private static final List<VendorEndpoint> VENDOR_ENDPOINTS = List.of(
            new VendorEndpoint("POST", "/api/stations/1/spirits"),
            new VendorEndpoint("PUT", "/api/stations/1/spirits/5"),
            new VendorEndpoint("DELETE", "/api/stations/1/spirits/5"),
            new VendorEndpoint("PATCH", "/api/stations/1/spirits/5/availability"),
            new VendorEndpoint("POST", "/api/stations/1/mixers"),
            new VendorEndpoint("PUT", "/api/stations/1/mixers/3"),
            new VendorEndpoint("DELETE", "/api/stations/1/mixers/3"),
            new VendorEndpoint("PATCH", "/api/stations/1/mixers/3/availability"),
            new VendorEndpoint("POST", "/api/stations/1/premades"),
            new VendorEndpoint("PUT", "/api/stations/1/premades/7"),
            new VendorEndpoint("DELETE", "/api/stations/1/premades/7"),
            new VendorEndpoint("PATCH", "/api/stations/1/premades/7/availability"),
            new VendorEndpoint("PUT", "/api/stations/1/cup-price"),
            new VendorEndpoint("PUT", "/api/stations/1/pickup-window"),
            new VendorEndpoint("PATCH", "/api/orders/1/state"),
            new VendorEndpoint("GET", "/api/stations/1/orders")
    );

    // Customer endpoints that should bypass the filter
    private static final List<VendorEndpoint> CUSTOMER_ENDPOINTS = List.of(
            new VendorEndpoint("GET", "/api/stations/1"),
            new VendorEndpoint("GET", "/api/stations/1/menu"),
            new VendorEndpoint("POST", "/api/stations/1/orders"),
            new VendorEndpoint("GET", "/api/orders/1"),
            new VendorEndpoint("PUT", "/api/orders/1/items"),
            new VendorEndpoint("POST", "/api/orders/1/checkout"),
            new VendorEndpoint("POST", "/api/orders/1/pay"),
            new VendorEndpoint("POST", "/api/orders/1/cancel"),
            new VendorEndpoint("POST", "/api/sessions"),
            new VendorEndpoint("GET", "/api/sessions/abc-123"),
            new VendorEndpoint("GET", "/api/sessions/abc-123/orders"),
            new VendorEndpoint("POST", "/api/auth/vendor/login"),
            new VendorEndpoint("POST", "/api/auth/vendor/refresh"),
            new VendorEndpoint("GET", "/actuator/health")
    );

    // --- Arbitraries ---

    @Provide
    Arbitrary<VendorEndpoint> vendorEndpoints() {
        return Arbitraries.of(VENDOR_ENDPOINTS);
    }

    @Provide
    Arbitrary<VendorEndpoint> customerEndpoints() {
        return Arbitraries.of(CUSTOMER_ENDPOINTS);
    }

    @Provide
    Arbitrary<Long> stationIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just("not-a-jwt"),
                Arbitraries.just("abc.def.ghi"),
                Arbitraries.strings().ofMinLength(10).ofMaxLength(50),
                Arbitraries.just("eyJhbGciOiJIUzI1NiJ9.invalid.signature")
        );
    }

    // --- Property 17a: Vendor endpoints with valid JWT return 200 (filter passes through) ---

    @Property
    void vendorEndpointsWithValidJwtPassThrough(
            @ForAll("vendorEndpoints") VendorEndpoint endpoint,
            @ForAll("stationIds") Long stationId) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        String validToken = createValidToken(stationId);
        when(authService.validateToken(validToken)).thenReturn(true);
        when(authService.getStationIdFromToken(validToken)).thenReturn(stationId);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        request.addHeader("Authorization", "Bearer " + validToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 200 :
                "Vendor endpoint " + endpoint.method() + " " + endpoint.path()
                        + " with valid JWT should pass through (200), got " + response.getStatus();
        assert filterChain.getRequest() != null :
                "Filter chain should have been invoked for valid JWT";
        assert request.getAttribute(JwtAuthFilter.STATION_ID_ATTRIBUTE).equals(stationId) :
                "Station ID attribute should be set on request";
    }

    // --- Property 17b: Vendor endpoints with invalid JWT return 401 ---

    @Property
    void vendorEndpointsWithInvalidJwtReturn401(
            @ForAll("vendorEndpoints") VendorEndpoint endpoint,
            @ForAll("invalidTokens") String invalidToken) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        when(authService.validateToken(anyString())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        request.addHeader("Authorization", "Bearer " + invalidToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 401 :
                "Vendor endpoint " + endpoint.method() + " " + endpoint.path()
                        + " with invalid JWT should return 401, got " + response.getStatus();
        assert filterChain.getRequest() == null :
                "Filter chain should NOT have been invoked for invalid JWT";
    }

    // --- Property 17c: Vendor endpoints with missing Authorization header return 401 ---

    @Property
    void vendorEndpointsWithMissingAuthHeaderReturn401(
            @ForAll("vendorEndpoints") VendorEndpoint endpoint) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        // No Authorization header set
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 401 :
                "Vendor endpoint " + endpoint.method() + " " + endpoint.path()
                        + " with missing auth header should return 401, got " + response.getStatus();
        assert filterChain.getRequest() == null :
                "Filter chain should NOT have been invoked for missing auth header";
    }

    // --- Property 17d: Vendor endpoints with expired JWT return 401 ---

    @Property
    void vendorEndpointsWithExpiredJwtReturn401(
            @ForAll("vendorEndpoints") VendorEndpoint endpoint,
            @ForAll("stationIds") Long stationId) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        String expiredToken = createExpiredToken(stationId);
        when(authService.validateToken(expiredToken)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 401 :
                "Vendor endpoint " + endpoint.method() + " " + endpoint.path()
                        + " with expired JWT should return 401, got " + response.getStatus();
        assert filterChain.getRequest() == null :
                "Filter chain should NOT have been invoked for expired JWT";
    }

    // --- Property 17e: Customer endpoints bypass filter regardless of token presence ---

    @Property
    void customerEndpointsBypassFilterWithoutToken(
            @ForAll("customerEndpoints") VendorEndpoint endpoint) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        // No Authorization header
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 200 :
                "Customer endpoint " + endpoint.method() + " " + endpoint.path()
                        + " should bypass filter (200), got " + response.getStatus();
        assert filterChain.getRequest() != null :
                "Filter chain should have been invoked for customer endpoint";
    }

    // --- Property 17f: Customer endpoints bypass filter even with invalid token ---

    @Property
    void customerEndpointsBypassFilterWithInvalidToken(
            @ForAll("customerEndpoints") VendorEndpoint endpoint,
            @ForAll("invalidTokens") String invalidToken) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        request.addHeader("Authorization", "Bearer " + invalidToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 200 :
                "Customer endpoint " + endpoint.method() + " " + endpoint.path()
                        + " should bypass filter even with invalid token (200), got " + response.getStatus();
        assert filterChain.getRequest() != null :
                "Filter chain should have been invoked for customer endpoint regardless of token";
    }

    // --- Property 17g: Vendor endpoints with malformed Authorization header (no Bearer prefix) return 401 ---

    @Property
    void vendorEndpointsWithMalformedAuthHeaderReturn401(
            @ForAll("vendorEndpoints") VendorEndpoint endpoint,
            @ForAll("stationIds") Long stationId) throws ServletException, IOException {

        AuthService authService = Mockito.mock(AuthService.class);
        JwtAuthFilter filter = new JwtAuthFilter(authService);

        String validToken = createValidToken(stationId);
        // Set header without "Bearer " prefix
        MockHttpServletRequest request = new MockHttpServletRequest(endpoint.method(), endpoint.path());
        request.addHeader("Authorization", validToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assert response.getStatus() == 401 :
                "Vendor endpoint with malformed auth header (no Bearer prefix) should return 401, got "
                        + response.getStatus();
        assert filterChain.getRequest() == null :
                "Filter chain should NOT have been invoked for malformed auth header";
    }

    // --- Helper methods ---

    private String createValidToken(Long stationId) {
        return Jwts.builder()
                .subject(stationId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 43200000)) // 12h from now
                .signWith(SIGNING_KEY)
                .compact();
    }

    private String createExpiredToken(Long stationId) {
        return Jwts.builder()
                .subject(stationId.toString())
                .issuedAt(new Date(System.currentTimeMillis() - 86400000)) // 24h ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1h ago
                .signWith(SIGNING_KEY)
                .compact();
    }

    private record VendorEndpoint(String method, String path) {}
}
