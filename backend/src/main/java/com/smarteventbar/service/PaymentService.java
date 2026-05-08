package com.smarteventbar.service;

import com.smarteventbar.dto.PaymentResult;
import com.smarteventbar.model.entity.CustomerOrder;

import java.util.Optional;
import java.util.UUID;

/**
 * Payment Service — simulated in Phase 1, with idempotency.
 *
 * Accepts payment requests and returns success after a brief delay.
 * Checks idempotency keys before processing: if a key has already been
 * processed for the same order and has not expired, the original response
 * is returned without reprocessing.
 *
 * Designed as an interface so a real payment provider can be swapped in later
 * without changing the order flow.
 */
public interface PaymentService {

    /**
     * Processes a payment for the given order using the provided idempotency key.
     *
     * If the idempotency key has already been processed for this order and has not
     * expired (within 24 hours), the original PaymentResult is returned.
     *
     * If the key is new, a simulated payment is processed (brief delay, then success),
     * and the key is persisted with the response and a 24-hour expiry.
     *
     * @param order          the order to process payment for
     * @param idempotencyKey the client-generated UUID idempotency key
     * @return the payment result
     */
    PaymentResult processPayment(CustomerOrder order, UUID idempotencyKey);

    /**
     * Looks up a previously processed payment by idempotency key for a specific order.
     *
     * Returns the cached PaymentResult if the key exists and has not expired.
     * Returns empty if the key does not exist or has expired.
     *
     * @param orderId        the order ID
     * @param idempotencyKey the idempotency key UUID
     * @return the cached payment result, or empty if not found or expired
     */
    Optional<PaymentResult> findByIdempotencyKey(Long orderId, UUID idempotencyKey);
}
