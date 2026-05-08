package com.smarteventbar.controller;

import com.smarteventbar.dto.OrderResponse;
import com.smarteventbar.dto.SessionResponse;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.service.OrderService;
import com.smarteventbar.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final OrderService orderService;

    public SessionController(SessionService sessionService, OrderService orderService) {
        this.sessionService = sessionService;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@RequestBody Map<String, Long> body) {
        Long stationId = body.get("stationId");
        if (stationId == null) {
            throw new IllegalArgumentException("stationId is required");
        }
        CustomerSession session = sessionService.createSession(stationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionResponse.fromEntity(session));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionId) {
        CustomerSession session = sessionService.getSession(sessionId);
        return ResponseEntity.ok(SessionResponse.fromEntity(session));
    }

    @GetMapping("/{sessionId}/orders")
    public ResponseEntity<List<OrderResponse>> getSessionOrders(@PathVariable String sessionId) {
        List<OrderResponse> orders = orderService.getOrdersBySession(sessionId).stream()
                .map(OrderResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(orders);
    }
}
