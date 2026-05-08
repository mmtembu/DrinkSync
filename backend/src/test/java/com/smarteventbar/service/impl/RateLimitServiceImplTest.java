package com.smarteventbar.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RateLimitServiceImpl}.
 * Tests sliding window rate limiting for order creation, payment attempts, and auth attempts.
 */
class RateLimitServiceImplTest {

    private RateLimitServiceImpl rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitServiceImpl();
    }

    // --- Order creation rate limiting (5/min/session) ---

    @Test
    void orderCreation_firstRequestAllowed() {
        assertTrue(rateLimitService.isOrderCreationAllowed("session-1"));
    }

    @Test
    void orderCreation_fiveRequestsAllowed() {
        String sessionId = "session-2";
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.isOrderCreationAllowed(sessionId),
                    "Request " + (i + 1) + " should be allowed");
            rateLimitService.recordOrderCreation(sessionId);
        }
    }

    @Test
    void orderCreation_sixthRequestRejected() {
        String sessionId = "session-3";
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordOrderCreation(sessionId);
        }
        assertFalse(rateLimitService.isOrderCreationAllowed(sessionId),
                "6th request should be rejected");
    }

    @Test
    void orderCreation_differentSessionsIndependent() {
        // Fill up session-a
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordOrderCreation("session-a");
        }
        // session-b should still be allowed
        assertTrue(rateLimitService.isOrderCreationAllowed("session-b"));
    }

    // --- Payment attempt rate limiting (3/min/order) ---

    @Test
    void paymentAttempt_firstRequestAllowed() {
        assertTrue(rateLimitService.isPaymentAttemptAllowed(1L));
    }

    @Test
    void paymentAttempt_threeRequestsAllowed() {
        Long orderId = 10L;
        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimitService.isPaymentAttemptAllowed(orderId),
                    "Request " + (i + 1) + " should be allowed");
            rateLimitService.recordPaymentAttempt(orderId);
        }
    }

    @Test
    void paymentAttempt_fourthRequestRejected() {
        Long orderId = 20L;
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordPaymentAttempt(orderId);
        }
        assertFalse(rateLimitService.isPaymentAttemptAllowed(orderId),
                "4th payment attempt should be rejected");
    }

    @Test
    void paymentAttempt_differentOrdersIndependent() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordPaymentAttempt(100L);
        }
        assertTrue(rateLimitService.isPaymentAttemptAllowed(200L));
    }

    // --- Auth attempt rate limiting (5 failed/15 min/station) ---

    @Test
    void authAttempt_firstAttemptAllowed() {
        assertTrue(rateLimitService.isAuthAttemptAllowed(1L));
    }

    @Test
    void authAttempt_fiveAttemptsAllowed() {
        Long stationId = 5L;
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.isAuthAttemptAllowed(stationId),
                    "Attempt " + (i + 1) + " should be allowed");
            rateLimitService.recordFailedAuthAttempt(stationId);
        }
    }

    @Test
    void authAttempt_sixthAttemptRejected() {
        Long stationId = 6L;
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAuthAttempt(stationId);
        }
        assertFalse(rateLimitService.isAuthAttemptAllowed(stationId),
                "6th auth attempt should be rejected");
    }

    @Test
    void authAttempt_differentStationsIndependent() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAuthAttempt(10L);
        }
        assertTrue(rateLimitService.isAuthAttemptAllowed(20L));
    }

    // --- Retry-after seconds ---

    @Test
    void retryAfterSeconds_orderCreation_returnsPositiveValue() {
        String sessionId = "retry-session";
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordOrderCreation(sessionId);
        }
        long retryAfter = rateLimitService.getOrderCreationRetryAfterSeconds(sessionId);
        assertTrue(retryAfter >= 1, "Retry-after should be at least 1 second");
        assertTrue(retryAfter <= 60, "Retry-after should be at most 60 seconds");
    }

    @Test
    void retryAfterSeconds_payment_returnsPositiveValue() {
        Long orderId = 50L;
        for (int i = 0; i < 3; i++) {
            rateLimitService.recordPaymentAttempt(orderId);
        }
        long retryAfter = rateLimitService.getPaymentRetryAfterSeconds(orderId);
        assertTrue(retryAfter >= 1);
        assertTrue(retryAfter <= 60);
    }

    @Test
    void retryAfterSeconds_auth_returnsPositiveValue() {
        Long stationId = 50L;
        for (int i = 0; i < 5; i++) {
            rateLimitService.recordFailedAuthAttempt(stationId);
        }
        long retryAfter = rateLimitService.getAuthRetryAfterSeconds(stationId);
        assertTrue(retryAfter >= 1);
        assertTrue(retryAfter <= 900);
    }

    @Test
    void retryAfterSeconds_noRecords_returnsDefault() {
        assertEquals(1, rateLimitService.getOrderCreationRetryAfterSeconds("nonexistent"));
        assertEquals(1, rateLimitService.getPaymentRetryAfterSeconds(999L));
        assertEquals(1, rateLimitService.getAuthRetryAfterSeconds(999L));
    }
}
