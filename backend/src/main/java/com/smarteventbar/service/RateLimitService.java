package com.smarteventbar.service;

/**
 * Enforces rate limits for order creation, payment attempts, and vendor authentication.
 * Uses sliding window counters to track request rates.
 * <p>
 * Validates: Requirements 12.3, 12.4, 17.6, 17.7
 * </p>
 */
public interface RateLimitService {

    /**
     * Checks whether order creation is allowed for the given session.
     * Limit: 5 requests per 60 seconds per session.
     *
     * @param sessionId the session identifier
     * @return true if the request is within the rate limit, false otherwise
     */
    boolean isOrderCreationAllowed(String sessionId);

    /**
     * Checks whether a payment attempt is allowed for the given order.
     * Limit: 3 requests per 60 seconds per order.
     *
     * @param orderId the order identifier
     * @return true if the request is within the rate limit, false otherwise
     */
    boolean isPaymentAttemptAllowed(Long orderId);

    /**
     * Checks whether an authentication attempt is allowed for the given station.
     * Limit: 5 failed attempts per 15 minutes (900 seconds) per station.
     *
     * @param stationId the station identifier
     * @return true if the attempt is within the rate limit, false otherwise
     */
    boolean isAuthAttemptAllowed(Long stationId);

    /**
     * Records an order creation request for the given session.
     *
     * @param sessionId the session identifier
     */
    void recordOrderCreation(String sessionId);

    /**
     * Records a payment attempt for the given order.
     *
     * @param orderId the order identifier
     */
    void recordPaymentAttempt(Long orderId);

    /**
     * Records a failed authentication attempt for the given station.
     *
     * @param stationId the station identifier
     */
    void recordFailedAuthAttempt(Long stationId);
}
