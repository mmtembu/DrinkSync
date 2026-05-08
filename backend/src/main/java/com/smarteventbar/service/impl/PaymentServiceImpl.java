package com.smarteventbar.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.PaymentResult;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.IdempotencyKey;
import com.smarteventbar.repository.IdempotencyKeyRepository;
import com.smarteventbar.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulated payment service for Phase 1.
 *
 * Accepts payment requests and returns success after a brief delay (200ms).
 * Checks idempotency keys before processing — if a key has already been
 * processed for the same order and has not expired, the original response
 * is returned without reprocessing.
 *
 * Idempotency keys are persisted with their associated response and a 24-hour expiry.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final long SIMULATED_DELAY_MS = 200;

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public PaymentServiceImpl(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentResult processPayment(CustomerOrder order, UUID idempotencyKey) {
        // Check idempotency key first
        Optional<PaymentResult> existingResult = findByIdempotencyKey(order.getId(), idempotencyKey);
        if (existingResult.isPresent()) {
            log.info("Idempotency key {} already processed for order {}, returning cached result",
                    idempotencyKey, order.getId());
            return existingResult.get();
        }

        // Simulate payment processing delay
        simulatePaymentDelay();

        // In Phase 1, payment always succeeds
        PaymentResult result = PaymentResult.success(order.getId(), order.getTotalPrice());

        // Persist idempotency key with response
        persistIdempotencyKey(order, idempotencyKey, result);

        log.info("Payment processed successfully for order {} with idempotency key {}",
                order.getId(), idempotencyKey);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResult> findByIdempotencyKey(Long orderId, UUID idempotencyKey) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository
                .findByOrderIdAndIdempotencyKey(orderId, idempotencyKey);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKey key = existing.get();

        // Check if the key has expired (24-hour expiry)
        if (key.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.info("Idempotency key {} for order {} has expired", idempotencyKey, orderId);
            return Optional.empty();
        }

        // Deserialize the cached response
        return Optional.of(deserializePaymentResult(key));
    }

    private void simulatePaymentDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Payment simulation delay interrupted");
        }
    }

    private void persistIdempotencyKey(CustomerOrder order, UUID idempotencyKey, PaymentResult result) {
        String responseBody = serializePaymentResult(result);
        IdempotencyKey key = new IdempotencyKey(order, idempotencyKey, responseBody, result.getStatusCode());
        idempotencyKeyRepository.save(key);
    }

    private String serializePaymentResult(PaymentResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize PaymentResult", e);
            throw new RuntimeException("Failed to serialize payment result", e);
        }
    }

    private PaymentResult deserializePaymentResult(IdempotencyKey key) {
        if (key.getResponseBody() != null) {
            try {
                return objectMapper.readValue(key.getResponseBody(), PaymentResult.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached PaymentResult for key {}, reconstructing",
                        key.getIdempotencyKey(), e);
            }
        }

        // Fallback: reconstruct from stored status code
        if (key.getResponseStatus() != null && key.getResponseStatus() == 200) {
            return PaymentResult.success(key.getOrder().getId(), key.getOrder().getTotalPrice());
        }
        return PaymentResult.failure(key.getOrder().getId(), key.getOrder().getTotalPrice(), "Payment failed");
    }
}
