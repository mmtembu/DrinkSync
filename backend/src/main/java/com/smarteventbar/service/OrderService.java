package com.smarteventbar.service;

import com.smarteventbar.dto.OrderItemRequest;
import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    CustomerOrder createOrder(Long stationId, String sessionId, List<OrderItemRequest> items);

    CustomerOrder getOrder(Long orderId);

    CustomerOrder updateOrderItems(Long orderId, String sessionId, List<OrderItemRequest> items);

    CustomerOrder checkout(Long orderId, String sessionId);

    CustomerOrder confirmPayment(Long orderId, String sessionId, UUID idempotencyKey);

    CustomerOrder cancelOrder(Long orderId, String sessionId);

    CustomerOrder transitionState(Long orderId, OrderState targetState);

    List<CustomerOrder> getOrdersByStationAndState(Long stationId, OrderState state);

    List<CustomerOrder> getActiveOrdersByStation(Long stationId);

    List<CustomerOrder> getOrdersBySession(String sessionId);

    void expireOrder(Long orderId);
}
