package com.smarteventbar.config;

import com.smarteventbar.service.impl.RateLimitServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter}.
 * Verifies that the filter enforces rate limits for order creation and payment endpoints.
 */
class RateLimitFilterTest {

    private RateLimitServiceImpl rateLimitService;
    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitServiceImpl();
        filter = new RateLimitFilter(rateLimitService);
        filterChain = mock(FilterChain.class);
    }

    // --- Order creation rate limiting ---

    @Test
    void orderCreation_withinLimit_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stations/1/orders");
        request.addHeader("X-Session-Id", "session-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void orderCreation_exceedsLimit_returns429() throws ServletException, IOException {
        String sessionId = "session-limit";

        // Make 5 requests (filling the limit)
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/stations/1/orders");
            req.addHeader("X-Session-Id", sessionId);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, filterChain);
        }

        // 6th request should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stations/1/orders");
        request.addHeader("X-Session-Id", sessionId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Rate limit exceeded"));
        assertTrue(response.getContentAsString().contains("retryAfterSeconds"));
        assertNotNull(response.getHeader("Retry-After"));
    }

    @Test
    void orderCreation_noSessionHeader_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stations/1/orders");
        // No X-Session-Id header
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void orderCreation_getRequest_notRateLimited() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stations/1/orders");
        request.addHeader("X-Session-Id", "session-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // --- Payment rate limiting ---

    @Test
    void payment_withinLimit_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/1/pay");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void payment_exceedsLimit_returns429() throws ServletException, IOException {
        Long orderId = 42L;

        // Make 3 requests (filling the limit)
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders/" + orderId + "/pay");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, filterChain);
        }

        // 4th request should be rejected
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/" + orderId + "/pay");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Payment rate limit exceeded"));
        assertTrue(response.getContentAsString().contains("retryAfterSeconds"));
    }

    // --- Non-rate-limited endpoints pass through ---

    @Test
    void nonRateLimitedEndpoint_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stations/1/menu");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authEndpoint_notHandledByFilter() throws ServletException, IOException {
        // Auth rate limiting is handled in the controller, not the filter
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/vendor/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // --- Different sessions/orders are independent ---

    @Test
    void orderCreation_differentSessions_independentLimits() throws ServletException, IOException {
        // Fill up session-a
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/stations/1/orders");
            req.addHeader("X-Session-Id", "session-a");
            filter.doFilterInternal(req, new MockHttpServletResponse(), filterChain);
        }

        // session-b should still work
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/stations/1/orders");
        request.addHeader("X-Session-Id", "session-b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, atLeast(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void payment_differentOrders_independentLimits() throws ServletException, IOException {
        // Fill up order 1
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders/1/pay");
            filter.doFilterInternal(req, new MockHttpServletResponse(), filterChain);
        }

        // Order 2 should still work
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/2/pay");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, atLeast(1)).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }
}
