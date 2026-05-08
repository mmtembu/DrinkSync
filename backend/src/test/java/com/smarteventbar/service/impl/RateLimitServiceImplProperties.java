package com.smarteventbar.service.impl;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for RateLimitService.
 *
 * Property 18: Rate limiting enforcement
 * - Generate sequences of order creation requests (1–10 per session); verify 1–5 succeed, 6+ rejected
 * - Generate sequences of payment attempts (1–5 per order); verify 1–3 succeed, 4+ rejected
 *
 * Property 25: Vendor authentication rate limiting
 * - Generate sequences of failed auth attempts (1–10 per station); verify 1–5 return auth error, 6+ return 429
 *
 * Validates: Requirements 12.3, 12.4, 17.6, 17.7
 */
class RateLimitServiceImplProperties {

    // =====================================================================
    // Property 18a: Order creation — requests 1–5 are allowed
    // =====================================================================

    /**
     * For any session and any request count between 1 and 5,
     * all order creation requests within the limit should be allowed.
     *
     * Validates: Requirements 12.3
     */
    @Property
    void orderCreationRequestsWithinLimitAreAllowed(
            @ForAll("sessionIds") String sessionId,
            @ForAll @IntRange(min = 1, max = 5) int requestCount) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        for (int i = 0; i < requestCount; i++) {
            assertThat(service.isOrderCreationAllowed(sessionId))
                    .as("Order creation request %d of %d should be allowed for session %s",
                            i + 1, requestCount, sessionId)
                    .isTrue();
            service.recordOrderCreation(sessionId);
        }
    }

    // =====================================================================
    // Property 18b: Order creation — requests 6+ are rejected
    // =====================================================================

    /**
     * For any session and any total request count between 6 and 10,
     * the first 5 requests succeed and all subsequent requests are rejected.
     *
     * Validates: Requirements 12.3
     */
    @Property
    void orderCreationRequestsBeyondLimitAreRejected(
            @ForAll("sessionIds") String sessionId,
            @ForAll @IntRange(min = 6, max = 10) int totalRequests) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // First 5 should succeed
        for (int i = 0; i < 5; i++) {
            assertThat(service.isOrderCreationAllowed(sessionId))
                    .as("Order creation request %d should be allowed", i + 1)
                    .isTrue();
            service.recordOrderCreation(sessionId);
        }

        // Requests 6+ should be rejected
        for (int i = 5; i < totalRequests; i++) {
            assertThat(service.isOrderCreationAllowed(sessionId))
                    .as("Order creation request %d should be rejected (limit is 5)", i + 1)
                    .isFalse();
        }
    }

    // =====================================================================
    // Property 18c: Payment attempts — requests 1–3 are allowed
    // =====================================================================

    /**
     * For any order and any attempt count between 1 and 3,
     * all payment attempts within the limit should be allowed.
     *
     * Validates: Requirements 12.4
     */
    @Property
    void paymentAttemptsWithinLimitAreAllowed(
            @ForAll("orderIds") Long orderId,
            @ForAll @IntRange(min = 1, max = 3) int attemptCount) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        for (int i = 0; i < attemptCount; i++) {
            assertThat(service.isPaymentAttemptAllowed(orderId))
                    .as("Payment attempt %d of %d should be allowed for order %d",
                            i + 1, attemptCount, orderId)
                    .isTrue();
            service.recordPaymentAttempt(orderId);
        }
    }

    // =====================================================================
    // Property 18d: Payment attempts — requests 4+ are rejected
    // =====================================================================

    /**
     * For any order and any total attempt count between 4 and 5,
     * the first 3 attempts succeed and all subsequent attempts are rejected.
     *
     * Validates: Requirements 12.4
     */
    @Property
    void paymentAttemptsBeyondLimitAreRejected(
            @ForAll("orderIds") Long orderId,
            @ForAll @IntRange(min = 4, max = 5) int totalAttempts) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // First 3 should succeed
        for (int i = 0; i < 3; i++) {
            assertThat(service.isPaymentAttemptAllowed(orderId))
                    .as("Payment attempt %d should be allowed", i + 1)
                    .isTrue();
            service.recordPaymentAttempt(orderId);
        }

        // Attempts 4+ should be rejected
        for (int i = 3; i < totalAttempts; i++) {
            assertThat(service.isPaymentAttemptAllowed(orderId))
                    .as("Payment attempt %d should be rejected (limit is 3)", i + 1)
                    .isFalse();
        }
    }

    // =====================================================================
    // Property 18e: Rate limits are independent across sessions/orders
    // =====================================================================

    /**
     * For any two distinct sessions, exhausting the rate limit on one session
     * does not affect the other session's allowance.
     *
     * Validates: Requirements 12.3
     */
    @Property
    void orderCreationRateLimitsAreIndependentAcrossSessions(
            @ForAll("sessionIds") String sessionA,
            @ForAll("sessionIds") String sessionB) {

        Assume.that(!sessionA.equals(sessionB));

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // Exhaust session A's limit
        for (int i = 0; i < 5; i++) {
            service.recordOrderCreation(sessionA);
        }
        assertThat(service.isOrderCreationAllowed(sessionA)).isFalse();

        // Session B should still be allowed
        assertThat(service.isOrderCreationAllowed(sessionB))
                .as("Session B should not be affected by session A's rate limit")
                .isTrue();
    }

    /**
     * For any two distinct orders, exhausting the payment rate limit on one order
     * does not affect the other order's allowance.
     *
     * Validates: Requirements 12.4
     */
    @Property
    void paymentRateLimitsAreIndependentAcrossOrders(
            @ForAll("orderIds") Long orderA,
            @ForAll("orderIds") Long orderB) {

        Assume.that(!orderA.equals(orderB));

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // Exhaust order A's limit
        for (int i = 0; i < 3; i++) {
            service.recordPaymentAttempt(orderA);
        }
        assertThat(service.isPaymentAttemptAllowed(orderA)).isFalse();

        // Order B should still be allowed
        assertThat(service.isPaymentAttemptAllowed(orderB))
                .as("Order B should not be affected by order A's rate limit")
                .isTrue();
    }

    // =====================================================================
    // Property 25a: Auth attempts — attempts 1–5 are allowed
    // =====================================================================

    /**
     * For any station and any attempt count between 1 and 5,
     * all failed auth attempts within the limit should be allowed (return auth error, not 429).
     *
     * Validates: Requirements 17.6
     */
    @Property
    void authAttemptsWithinLimitAreAllowed(
            @ForAll("stationIds") Long stationId,
            @ForAll @IntRange(min = 1, max = 5) int attemptCount) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        for (int i = 0; i < attemptCount; i++) {
            assertThat(service.isAuthAttemptAllowed(stationId))
                    .as("Auth attempt %d of %d should be allowed for station %d",
                            i + 1, attemptCount, stationId)
                    .isTrue();
            service.recordFailedAuthAttempt(stationId);
        }
    }

    // =====================================================================
    // Property 25b: Auth attempts — attempts 6+ are rejected (429)
    // =====================================================================

    /**
     * For any station and any total attempt count between 6 and 10,
     * the first 5 failed attempts are allowed (return auth error) and
     * all subsequent attempts are rejected (would return 429).
     *
     * Validates: Requirements 17.6, 17.7
     */
    @Property
    void authAttemptsBeyondLimitAreRejected(
            @ForAll("stationIds") Long stationId,
            @ForAll @IntRange(min = 6, max = 10) int totalAttempts) {

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // First 5 should be allowed (return auth error, not 429)
        for (int i = 0; i < 5; i++) {
            assertThat(service.isAuthAttemptAllowed(stationId))
                    .as("Auth attempt %d should be allowed (returns auth error)", i + 1)
                    .isTrue();
            service.recordFailedAuthAttempt(stationId);
        }

        // Attempts 6+ should be rejected (would return 429)
        for (int i = 5; i < totalAttempts; i++) {
            assertThat(service.isAuthAttemptAllowed(stationId))
                    .as("Auth attempt %d should be rejected with 429 (limit is 5)", i + 1)
                    .isFalse();
        }
    }

    // =====================================================================
    // Property 25c: Auth rate limits are independent across stations
    // =====================================================================

    /**
     * For any two distinct stations, exhausting the auth rate limit on one station
     * does not affect the other station's allowance.
     *
     * Validates: Requirements 17.6
     */
    @Property
    void authRateLimitsAreIndependentAcrossStations(
            @ForAll("stationIds") Long stationA,
            @ForAll("stationIds") Long stationB) {

        Assume.that(!stationA.equals(stationB));

        RateLimitServiceImpl service = new RateLimitServiceImpl();

        // Exhaust station A's auth limit
        for (int i = 0; i < 5; i++) {
            service.recordFailedAuthAttempt(stationA);
        }
        assertThat(service.isAuthAttemptAllowed(stationA)).isFalse();

        // Station B should still be allowed
        assertThat(service.isAuthAttemptAllowed(stationB))
                .as("Station B should not be affected by station A's auth rate limit")
                .isTrue();
    }

    // =====================================================================
    // Arbitraries / Providers
    // =====================================================================

    @Provide
    Arbitrary<String> sessionIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('0', '9')
                .withChars('-')
                .ofMinLength(8)
                .ofMaxLength(36);
    }

    @Provide
    Arbitrary<Long> orderIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Long> stationIds() {
        return Arbitraries.longs().between(1L, 1000L);
    }
}
