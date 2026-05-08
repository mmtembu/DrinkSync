package com.smarteventbar.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.PaymentResult;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.IdempotencyKey;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.IdempotencyKeyRepository;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for PaymentServiceImpl — payment idempotency and key expiry.
 *
 * Property 26: Payment idempotency
 * - Generate random orders and UUID keys; submit payment twice with same key for same order; verify same response
 * - Submit same key for different order; verify independent processing
 *
 * Property 27: Idempotency key expiry
 * - Generate keys with varying ages (0–48 hours); verify keys > 24 hours treated as expired
 *
 * Validates: Requirements 18.2, 18.3, 18.4
 */
class PaymentServiceImplProperties {

    private IdempotencyKeyRepository idempotencyKeyRepository;
    private ObjectMapper objectMapper;
    private PaymentServiceImpl paymentService;

    @BeforeProperty
    void setUp() {
        idempotencyKeyRepository = Mockito.mock(IdempotencyKeyRepository.class);
        objectMapper = new ObjectMapper();
        paymentService = new PaymentServiceImpl(idempotencyKeyRepository, objectMapper);
    }

    // --- Arbitraries ---

    @Provide
    Arbitrary<Long> orderIds() {
        return Arbitraries.longs().between(1L, 100_000L);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("9999.99"))
                .ofScale(2);
    }

    @Provide
    Arbitrary<UUID> uuids() {
        return Arbitraries.create(UUID::randomUUID);
    }

    @Provide
    Arbitrary<Integer> keyAgeHours() {
        return Arbitraries.integers().between(0, 48);
    }

    // --- Helper methods ---

    private CustomerOrder createOrder(Long id, BigDecimal totalPrice) {
        Station station = new Station();
        station.setId(1L);

        CustomerSession session = new CustomerSession();

        CustomerOrder order = new CustomerOrder(station, session);
        order.setId(id);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private IdempotencyKey createStoredKey(CustomerOrder order, UUID key, PaymentResult result, int ageHours) {
        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        IdempotencyKey idempotencyKey = new IdempotencyKey(order, key, responseBody, result.getStatusCode());
        // Override timestamps to simulate age
        LocalDateTime createdAt = LocalDateTime.now().minusHours(ageHours);
        idempotencyKey.setCreatedAt(createdAt);
        idempotencyKey.setExpiresAt(createdAt.plusHours(24));
        return idempotencyKey;
    }

    // =====================================================================
    // Property 26: Payment idempotency
    // =====================================================================

    /**
     * Property 26a: Submitting the same idempotency key for the same order twice
     * returns the same response without reprocessing.
     *
     * **Validates: Requirements 18.2**
     */
    @Property
    void sameKeyForSameOrderReturnsSameResponse(
            @ForAll("orderIds") Long orderId,
            @ForAll("prices") BigDecimal price,
            @ForAll("uuids") UUID idempotencyKey) {

        // Fresh mocks per try to avoid accumulation across jqwik tries
        IdempotencyKeyRepository localRepo = Mockito.mock(IdempotencyKeyRepository.class);
        PaymentServiceImpl localService = new PaymentServiceImpl(localRepo, objectMapper);

        CustomerOrder order = createOrder(orderId, price);

        // First call: no existing key — processes payment and saves
        when(localRepo.findByOrderIdAndIdempotencyKey(eq(orderId), eq(idempotencyKey)))
                .thenReturn(Optional.empty());
        when(localRepo.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult firstResult = localService.processPayment(order, idempotencyKey);

        // Capture what was saved so we can return it on the second call
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(localRepo).save(captor.capture());
        IdempotencyKey savedKey = captor.getValue();

        // Second call: existing non-expired key found — returns cached result
        Mockito.reset(localRepo);
        when(localRepo.findByOrderIdAndIdempotencyKey(eq(orderId), eq(idempotencyKey)))
                .thenReturn(Optional.of(savedKey));

        PaymentResult secondResult = localService.processPayment(order, idempotencyKey);

        // Both results must be equal
        assert firstResult.equals(secondResult) :
                "Expected same response for duplicate idempotency key. First: " + firstResult + ", Second: " + secondResult;

        // Verify no additional save on the second call (no reprocessing)
        verify(localRepo, never()).save(any());
    }

    /**
     * Property 26b: The same idempotency key used for a different order is treated
     * as a new, independent request.
     *
     * **Validates: Requirements 18.3**
     */
    @Property
    void sameKeyForDifferentOrdersProcessedIndependently(
            @ForAll("orderIds") Long orderId1,
            @ForAll("prices") BigDecimal price1,
            @ForAll("prices") BigDecimal price2,
            @ForAll("uuids") UUID idempotencyKey) {

        // Fresh mocks per try to avoid accumulation across jqwik tries
        IdempotencyKeyRepository localRepo = Mockito.mock(IdempotencyKeyRepository.class);
        PaymentServiceImpl localService = new PaymentServiceImpl(localRepo, objectMapper);

        // Ensure distinct order IDs
        Long orderId2 = orderId1 + 1;

        CustomerOrder order1 = createOrder(orderId1, price1);
        CustomerOrder order2 = createOrder(orderId2, price2);

        // Both lookups return empty — each order has no prior key
        when(localRepo.findByOrderIdAndIdempotencyKey(eq(orderId1), eq(idempotencyKey)))
                .thenReturn(Optional.empty());
        when(localRepo.findByOrderIdAndIdempotencyKey(eq(orderId2), eq(idempotencyKey)))
                .thenReturn(Optional.empty());
        when(localRepo.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult result1 = localService.processPayment(order1, idempotencyKey);
        PaymentResult result2 = localService.processPayment(order2, idempotencyKey);

        // Both should succeed independently
        assert result1.isSuccess() :
                "Payment for order " + orderId1 + " should succeed";
        assert result2.isSuccess() :
                "Payment for order " + orderId2 + " should succeed";

        // Results should reference their respective orders
        assert result1.getOrderId().equals(orderId1) :
                "Result 1 should reference order " + orderId1 + " but got " + result1.getOrderId();
        assert result2.getOrderId().equals(orderId2) :
                "Result 2 should reference order " + orderId2 + " but got " + result2.getOrderId();

        // Both payments should have been persisted (two saves)
        verify(localRepo, times(2)).save(any(IdempotencyKey.class));
    }

    // =====================================================================
    // Property 27: Idempotency key expiry
    // =====================================================================

    /**
     * Property 27a: Keys aged <= 24 hours are treated as valid (non-expired)
     * and return the cached response.
     *
     * **Validates: Requirements 18.4**
     */
    @Property
    void keysWithin24HoursReturnCachedResponse(
            @ForAll("orderIds") Long orderId,
            @ForAll("prices") BigDecimal price,
            @ForAll("uuids") UUID idempotencyKey) {

        // Use age 0–23 hours (within 24-hour window)
        int ageHours = Arbitraries.integers().between(0, 23).sample();

        CustomerOrder order = createOrder(orderId, price);
        PaymentResult cachedResult = PaymentResult.success(orderId, price);
        IdempotencyKey storedKey = createStoredKey(order, idempotencyKey, cachedResult, ageHours);

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(eq(orderId), eq(idempotencyKey)))
                .thenReturn(Optional.of(storedKey));

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(orderId, idempotencyKey);

        assert result.isPresent() :
                "Key aged " + ageHours + " hours should NOT be expired (within 24h window)";
        assert result.get().isSuccess() :
                "Cached result should indicate success";
        assert result.get().getOrderId().equals(orderId) :
                "Cached result should reference order " + orderId;
    }

    /**
     * Property 27b: Keys aged > 24 hours are treated as expired and return empty.
     *
     * **Validates: Requirements 18.4**
     */
    @Property
    void keysOlderThan24HoursAreTreatedAsExpired(
            @ForAll("orderIds") Long orderId,
            @ForAll("prices") BigDecimal price,
            @ForAll("uuids") UUID idempotencyKey) {

        // Use age 25–48 hours (past 24-hour window)
        int ageHours = Arbitraries.integers().between(25, 48).sample();

        CustomerOrder order = createOrder(orderId, price);
        PaymentResult cachedResult = PaymentResult.success(orderId, price);
        IdempotencyKey storedKey = createStoredKey(order, idempotencyKey, cachedResult, ageHours);

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(eq(orderId), eq(idempotencyKey)))
                .thenReturn(Optional.of(storedKey));

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(orderId, idempotencyKey);

        assert result.isEmpty() :
                "Key aged " + ageHours + " hours should be expired (> 24h). Got: " + result;
    }

    /**
     * Property 27c: Expired keys cause processPayment to reprocess the payment
     * (treat as new request).
     *
     * **Validates: Requirements 18.4**
     */
    @Property
    void expiredKeysCauseReprocessing(
            @ForAll("orderIds") Long orderId,
            @ForAll("prices") BigDecimal price,
            @ForAll("uuids") UUID idempotencyKey) {

        // Fresh mocks per try to avoid accumulation across jqwik tries
        IdempotencyKeyRepository localRepo = Mockito.mock(IdempotencyKeyRepository.class);
        PaymentServiceImpl localService = new PaymentServiceImpl(localRepo, objectMapper);

        // Use age 25–48 hours (past 24-hour window)
        int ageHours = Arbitraries.integers().between(25, 48).sample();

        CustomerOrder order = createOrder(orderId, price);
        PaymentResult cachedResult = PaymentResult.success(orderId, price);
        IdempotencyKey expiredKey = createStoredKey(order, idempotencyKey, cachedResult, ageHours);

        // findByOrderIdAndIdempotencyKey returns the expired key
        when(localRepo.findByOrderIdAndIdempotencyKey(eq(orderId), eq(idempotencyKey)))
                .thenReturn(Optional.of(expiredKey));
        when(localRepo.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult result = localService.processPayment(order, idempotencyKey);

        // Should succeed (reprocessed)
        assert result.isSuccess() :
                "Expired key should cause reprocessing, resulting in success";

        // A new key should have been saved (reprocessing occurred)
        verify(localRepo).save(any(IdempotencyKey.class));
    }
}
