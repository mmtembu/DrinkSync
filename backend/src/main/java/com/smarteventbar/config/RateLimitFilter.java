package com.smarteventbar.config;

import com.smarteventbar.service.impl.RateLimitServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring filter that enforces rate limits before requests reach controllers.
 * <p>
 * Handles:
 * <ul>
 *   <li>POST /api/stations/{id}/orders → order creation rate limit (5/min/session)</li>
 *   <li>POST /api/orders/{id}/pay → payment attempt rate limit (3/min/order)</li>
 * </ul>
 * <p>
 * Auth rate limiting (5 failed/15 min/station) is handled in the auth controller
 * since it requires parsing the request body for the station ID.
 * </p>
 * <p>
 * This filter runs BEFORE the JwtAuthFilter (lower @Order value = higher priority).
 * </p>
 * <p>
 * Validates: Requirements 12.3, 12.4
 * </p>
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String SESSION_ID_HEADER = "X-Session-Id";

    /**
     * Matches POST /api/stations/{stationId}/orders
     */
    private static final Pattern ORDER_CREATION_PATTERN =
            Pattern.compile("^/api/stations/(\\d+)/orders$");

    /**
     * Matches POST /api/orders/{orderId}/pay
     */
    private static final Pattern PAYMENT_PATTERN =
            Pattern.compile("^/api/orders/(\\d+)/pay$");

    private final RateLimitServiceImpl rateLimitService;

    public RateLimitFilter(RateLimitServiceImpl rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String method = request.getMethod();
        String path = request.getRequestURI();

        // Only check POST requests
        if ("POST".equalsIgnoreCase(method)) {

            // Check order creation rate limit
            Matcher orderMatcher = ORDER_CREATION_PATTERN.matcher(path);
            if (orderMatcher.matches()) {
                String sessionId = request.getHeader(SESSION_ID_HEADER);
                if (sessionId != null && !sessionId.isBlank()) {
                    if (!rateLimitService.isOrderCreationAllowed(sessionId)) {
                        long retryAfter = rateLimitService.getOrderCreationRetryAfterSeconds(sessionId);
                        log.warn("Order creation rate limit exceeded for session {}", sessionId);
                        sendRateLimitResponse(response, "Rate limit exceeded", retryAfter);
                        return;
                    }
                    // Record the request (count it toward the limit)
                    rateLimitService.recordOrderCreation(sessionId);
                }
            }

            // Check payment attempt rate limit
            Matcher paymentMatcher = PAYMENT_PATTERN.matcher(path);
            if (paymentMatcher.matches()) {
                String orderIdStr = paymentMatcher.group(1);
                try {
                    Long orderId = Long.parseLong(orderIdStr);
                    if (!rateLimitService.isPaymentAttemptAllowed(orderId)) {
                        long retryAfter = rateLimitService.getPaymentRetryAfterSeconds(orderId);
                        log.warn("Payment rate limit exceeded for order {}", orderId);
                        sendRateLimitResponse(response, "Payment rate limit exceeded", retryAfter);
                        return;
                    }
                    // Record the attempt (count it toward the limit)
                    rateLimitService.recordPaymentAttempt(orderId);
                } catch (NumberFormatException e) {
                    // Invalid order ID format — let the controller handle it
                    log.debug("Non-numeric order ID in payment path: {}", orderIdStr);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sends a 429 Too Many Requests response with a JSON body.
     */
    private void sendRateLimitResponse(HttpServletResponse response, String error,
                                       long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"error\":\"" + error + "\",\"retryAfterSeconds\":" + retryAfterSeconds + "}"
        );
    }
}
