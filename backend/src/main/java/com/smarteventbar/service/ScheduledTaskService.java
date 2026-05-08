package com.smarteventbar.service;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled background tasks for:
 * - Pickup window expiry (READY → EXPIRED)
 * - Session expiry (inactive > 2 hours → cancel DRAFT orders)
 */
@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final SessionService sessionService;

    public ScheduledTaskService(OrderRepository orderRepository,
                                OrderService orderService,
                                SessionService sessionService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.sessionService = sessionService;
    }

    /**
     * Runs every 30 seconds to check for READY orders past their pickup window.
     */
    @Scheduled(fixedRate = 30000)
    public void expireReadyOrders() {
        List<CustomerOrder> readyOrders = orderRepository.findByState(OrderState.READY);

        for (CustomerOrder order : readyOrders) {
            if (order.getPickupWindowStart() == null) {
                continue;
            }
            int windowMinutes = order.getStation().getPickupWindowMinutes();
            LocalDateTime expiresAt = order.getPickupWindowStart().plusMinutes(windowMinutes);

            if (expiresAt.isBefore(LocalDateTime.now())) {
                try {
                    orderService.expireOrder(order.getId());
                    log.info("Expired order {} (pickup window elapsed)", order.getId());
                } catch (Exception e) {
                    log.error("Failed to expire order {}", order.getId(), e);
                }
            }
        }
    }

    /**
     * Runs every 60 seconds to expire inactive sessions and cancel their DRAFT orders.
     */
    @Scheduled(fixedRate = 60000)
    public void expireInactiveSessions() {
        try {
            sessionService.expireInactiveSessions();
        } catch (Exception e) {
            log.error("Failed to expire inactive sessions", e);
        }
    }
}
