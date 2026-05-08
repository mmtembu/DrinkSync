package com.smarteventbar.config;

import com.smarteventbar.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for vendor endpoints.
 * <p>
 * Intercepts requests to vendor-protected endpoints and validates the JWT token
 * from the Authorization header. Customer endpoints bypass this filter entirely.
 * </p>
 * <p>
 * Validates: Requirements 12.1, 12.2, 12.5, 12.6
 * </p>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    public static final String STATION_ID_ATTRIBUTE = "authenticatedStationId";

    private final AuthService authService;

    /**
     * Each entry defines a vendor-protected route pattern.
     * Format: "METHOD /path/pattern" where {id} is a wildcard for a single path segment
     * and /** means any trailing path segments.
     */
    private static final List<VendorRoute> VENDOR_ROUTES = List.of(
            // Menu management — spirits
            new VendorRoute("POST", "/api/stations/{id}/spirits"),
            new VendorRoute("PUT", "/api/stations/{id}/spirits/**"),
            new VendorRoute("DELETE", "/api/stations/{id}/spirits/**"),
            new VendorRoute("PATCH", "/api/stations/{id}/spirits/**"),
            // Menu management — mixers
            new VendorRoute("POST", "/api/stations/{id}/mixers"),
            new VendorRoute("PUT", "/api/stations/{id}/mixers/**"),
            new VendorRoute("DELETE", "/api/stations/{id}/mixers/**"),
            new VendorRoute("PATCH", "/api/stations/{id}/mixers/**"),
            // Menu management — premades
            new VendorRoute("POST", "/api/stations/{id}/premades"),
            new VendorRoute("PUT", "/api/stations/{id}/premades/**"),
            new VendorRoute("DELETE", "/api/stations/{id}/premades/**"),
            new VendorRoute("PATCH", "/api/stations/{id}/premades/**"),
            // Station settings
            new VendorRoute("PUT", "/api/stations/{id}/cup-price"),
            new VendorRoute("PUT", "/api/stations/{id}/pickup-window"),
            // Vendor order management
            new VendorRoute("PATCH", "/api/orders/{id}/state"),
            new VendorRoute("GET", "/api/stations/{id}/orders")
    );

    public JwtAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return !isVendorEndpoint(method, path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // Missing Authorization header
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Missing or malformed Authorization header for vendor endpoint: {} {}",
                    request.getMethod(), request.getRequestURI());
            sendUnauthorized(response, "Authentication required");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        // Validate the token
        if (!authService.validateToken(token)) {
            log.debug("Invalid or expired JWT token for vendor endpoint: {} {}",
                    request.getMethod(), request.getRequestURI());
            sendUnauthorized(response, "Token expired, re-authentication required");
            return;
        }

        // Extract station ID from token and set as request attribute
        Long stationId = authService.getStationIdFromToken(token);
        request.setAttribute(STATION_ID_ATTRIBUTE, stationId);

        log.debug("Authenticated vendor request for station {} on {} {}",
                stationId, request.getMethod(), request.getRequestURI());

        filterChain.doFilter(request, response);
    }

    /**
     * Checks whether the given HTTP method + path matches any vendor-protected route.
     */
    private boolean isVendorEndpoint(String method, String path) {
        for (VendorRoute route : VENDOR_ROUTES) {
            if (route.matches(method, path)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    /**
     * Represents a vendor route pattern with method and path matching.
     */
    private record VendorRoute(String method, String pattern) {

        /**
         * Matches the given HTTP method and request path against this route pattern.
         * <p>
         * Pattern rules:
         * - {id} matches exactly one path segment (no slashes)
         * - /** matches any remaining path segments (greedy)
         * - Literal segments must match exactly
         */
        boolean matches(String httpMethod, String requestPath) {
            if (!this.method.equalsIgnoreCase(httpMethod)) {
                return false;
            }
            return matchPath(this.pattern, requestPath);
        }

        private boolean matchPath(String pattern, String path) {
            String[] patternParts = splitPath(pattern);
            String[] pathParts = splitPath(path);

            int pi = 0; // pattern index
            int ri = 0; // request path index

            while (pi < patternParts.length && ri < pathParts.length) {
                String pp = patternParts[pi];

                if ("**".equals(pp)) {
                    // ** matches everything remaining
                    return true;
                }

                if (pp.startsWith("{") && pp.endsWith("}")) {
                    // Wildcard segment — matches any single segment
                    pi++;
                    ri++;
                    continue;
                }

                // Literal match
                if (!pp.equals(pathParts[ri])) {
                    return false;
                }

                pi++;
                ri++;
            }

            // Both must be fully consumed (unless pattern ended with **)
            return pi == patternParts.length && ri == pathParts.length;
        }

        private String[] splitPath(String path) {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.isEmpty()) {
                return new String[0];
            }
            return path.split("/");
        }
    }
}
