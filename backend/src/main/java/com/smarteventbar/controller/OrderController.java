package com.smarteventbar.controller;

import com.smarteventbar.config.JwtAuthFilter;
import com.smarteventbar.dto.CheckoutRequest;
import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.dto.OrderResponse;
import com.smarteventbar.dto.StateTransitionRequest;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.validation.PhoneNumberValidator;
import com.smarteventbar.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {

    private static final String SESSION_ID_HEADER = "X-Session-Id";

    private final OrderService orderService;
    private final PhoneNumberValidator phoneNumberValidator;

    public OrderController(OrderService orderService, PhoneNumberValidator phoneNumberValidator) {
        this.orderService = orderService;
        this.phoneNumberValidator = phoneNumberValidator;
    }

    @PostMapping("/stations/{stationId}/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @PathVariable Long stationId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @Valid @RequestBody List<OrderItemRequest> items) {
        CustomerOrder order = orderService.createOrder(stationId, sessionId, items);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.fromEntity(order));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = SESSION_ID_HEADER, required = false) String sessionId) {
        CustomerOrder order = orderService.getOrder(orderId);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @PutMapping("/orders/{orderId}/items")
    public ResponseEntity<OrderResponse> updateOrderItems(
            @PathVariable Long orderId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @Valid @RequestBody List<OrderItemRequest> items) {
        CustomerOrder order = orderService.updateOrderItems(orderId, sessionId, items);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @PostMapping("/orders/{orderId}/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @PathVariable Long orderId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestBody(required = false) CheckoutRequest checkoutRequest) {

        String customerPhone = null;
        Boolean whatsappOptIn = null;

        if (checkoutRequest != null) {
            customerPhone = checkoutRequest.getCustomerPhone();
            whatsappOptIn = checkoutRequest.getWhatsappOptIn();
        }

        // Validate phone number format if provided
        if (customerPhone != null && !customerPhone.isBlank()) {
            ValidationResult validationResult = phoneNumberValidator.validate(customerPhone);
            if (!validationResult.valid()) {
                throw new IllegalArgumentException(validationResult.errorMessage());
            }
        }

        CustomerOrder order = orderService.checkout(orderId, sessionId, customerPhone, whatsappOptIn);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ResponseEntity<OrderResponse> pay(
            @PathVariable Long orderId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKeyStr) {
        UUID idempotencyKey = UUID.fromString(idempotencyKeyStr);
        CustomerOrder order = orderService.confirmPayment(orderId, sessionId, idempotencyKey);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long orderId,
            @RequestHeader(SESSION_ID_HEADER) String sessionId) {
        CustomerOrder order = orderService.cancelOrder(orderId, sessionId);
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @PatchMapping("/orders/{orderId}/state")
    public ResponseEntity<OrderResponse> transitionState(
            @PathVariable Long orderId,
            @Valid @RequestBody StateTransitionRequest request,
            HttpServletRequest httpRequest) {
        // Station ID is set by JwtAuthFilter
        Long authenticatedStationId = (Long) httpRequest.getAttribute(JwtAuthFilter.STATION_ID_ATTRIBUTE);
        CustomerOrder order = orderService.transitionState(orderId, request.getTargetState());
        return ResponseEntity.ok(OrderResponse.fromEntity(order));
    }

    @GetMapping("/stations/{stationId}/orders")
    public ResponseEntity<List<OrderResponse>> getStationOrders(
            @PathVariable Long stationId,
            @RequestParam(required = false) OrderState state) {
        List<CustomerOrder> orders;
        if (state != null) {
            orders = orderService.getOrdersByStationAndState(stationId, state);
        } else {
            orders = orderService.getActiveOrdersByStation(stationId);
        }
        return ResponseEntity.ok(orders.stream().map(OrderResponse::fromEntity).toList());
    }
}
