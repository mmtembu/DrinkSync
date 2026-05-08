package com.smarteventbar.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.PaymentResult;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.IdempotencyKey;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.repository.IdempotencyKeyRepository;
import com.smarteventbar.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private PaymentService paymentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        paymentService = new PaymentServiceImpl(idempotencyKeyRepository, objectMapper);
    }

    // --- processPayment tests ---

    @Test
    void processPayment_newKey_returnsSuccessAndPersistsKey() {
        CustomerOrder order = createOrder(1L, new BigDecimal("85.00"));
        UUID idempotencyKey = UUID.randomUUID();

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult result = paymentService.processPayment(order, idempotencyKey);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertEquals("Payment successful", result.getMessage());
        assertEquals(1L, result.getOrderId());
        assertEquals(0, new BigDecimal("85.00").compareTo(result.getAmount()));

        // Verify idempotency key was persisted
        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).save(captor.capture());
        IdempotencyKey savedKey = captor.getValue();
        assertEquals(idempotencyKey, savedKey.getIdempotencyKey());
        assertEquals(200, savedKey.getResponseStatus());
        assertNotNull(savedKey.getResponseBody());
    }

    @Test
    void processPayment_existingNonExpiredKey_returnsCachedResult() throws Exception {
        CustomerOrder order = createOrder(1L, new BigDecimal("85.00"));
        UUID idempotencyKey = UUID.randomUUID();

        PaymentResult cachedResult = PaymentResult.success(1L, new BigDecimal("85.00"));
        String cachedJson = objectMapper.writeValueAsString(cachedResult);

        IdempotencyKey existingKey = new IdempotencyKey(order, idempotencyKey, cachedJson, 200);
        // Key is not expired (created just now, expires in 24 hours)

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(existingKey));

        PaymentResult result = paymentService.processPayment(order, idempotencyKey);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());
        assertEquals(1L, result.getOrderId());

        // Verify no new key was saved (only the lookup happened)
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    void processPayment_expiredKey_processesNewPayment() throws Exception {
        CustomerOrder order = createOrder(1L, new BigDecimal("50.00"));
        UUID idempotencyKey = UUID.randomUUID();

        // Create an expired key (expired 1 hour ago)
        IdempotencyKey expiredKey = new IdempotencyKey(order, idempotencyKey, "{}", 200);
        expiredKey.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(expiredKey));
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResult result = paymentService.processPayment(order, idempotencyKey);

        assertTrue(result.isSuccess());
        assertEquals(200, result.getStatusCode());

        // Verify a new key was persisted (expired key was treated as non-existent)
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    // --- findByIdempotencyKey tests ---

    @Test
    void findByIdempotencyKey_noKeyExists_returnsEmpty() {
        UUID idempotencyKey = UUID.randomUUID();

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.empty());

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(1L, idempotencyKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdempotencyKey_validNonExpiredKey_returnsCachedResult() throws Exception {
        CustomerOrder order = createOrder(1L, new BigDecimal("75.00"));
        UUID idempotencyKey = UUID.randomUUID();

        PaymentResult cachedResult = PaymentResult.success(1L, new BigDecimal("75.00"));
        String cachedJson = objectMapper.writeValueAsString(cachedResult);

        IdempotencyKey existingKey = new IdempotencyKey(order, idempotencyKey, cachedJson, 200);

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(existingKey));

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(1L, idempotencyKey);

        assertTrue(result.isPresent());
        assertTrue(result.get().isSuccess());
        assertEquals(1L, result.get().getOrderId());
    }

    @Test
    void findByIdempotencyKey_expiredKey_returnsEmpty() {
        CustomerOrder order = createOrder(1L, new BigDecimal("75.00"));
        UUID idempotencyKey = UUID.randomUUID();

        IdempotencyKey expiredKey = new IdempotencyKey(order, idempotencyKey, "{}", 200);
        expiredKey.setExpiresAt(LocalDateTime.now().minusHours(1));

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(expiredKey));

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(1L, idempotencyKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByIdempotencyKey_nullResponseBody_reconstructsFromStatus() {
        CustomerOrder order = createOrder(1L, new BigDecimal("60.00"));
        UUID idempotencyKey = UUID.randomUUID();

        IdempotencyKey keyWithNullBody = new IdempotencyKey(order, idempotencyKey, null, 200);

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(keyWithNullBody));

        Optional<PaymentResult> result = paymentService.findByIdempotencyKey(1L, idempotencyKey);

        assertTrue(result.isPresent());
        assertTrue(result.get().isSuccess());
        assertEquals(1L, result.get().getOrderId());
    }

    @Test
    void processPayment_persistsKeyWith24HourExpiry() {
        CustomerOrder order = createOrder(1L, new BigDecimal("40.00"));
        UUID idempotencyKey = UUID.randomUUID();

        when(idempotencyKeyRepository.findByOrderIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.empty());
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.processPayment(order, idempotencyKey);

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(idempotencyKeyRepository).save(captor.capture());
        IdempotencyKey savedKey = captor.getValue();

        // Verify the key expires approximately 24 hours from now
        LocalDateTime expectedExpiry = LocalDateTime.now().plusHours(24);
        assertTrue(savedKey.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));
        assertTrue(savedKey.getExpiresAt().isBefore(expectedExpiry.plusMinutes(1)));
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
}
