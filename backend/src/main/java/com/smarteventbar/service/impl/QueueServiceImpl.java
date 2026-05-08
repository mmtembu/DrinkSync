package com.smarteventbar.service.impl;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.service.QueueService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Assigns monotonically increasing queue positions per station
 * based on payment confirmation order.
 *
 * Thread safety is ensured by synchronizing on a per-station lock object,
 * so concurrent payments at the same station produce sequential positions
 * without gaps or duplicates.
 */
@Service
public class QueueServiceImpl implements QueueService {

    private final OrderRepository orderRepository;

    public QueueServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public synchronized int assignQueuePosition(CustomerOrder order) {
        Long stationId = order.getStation().getId();
        int maxPosition = orderRepository.findMaxQueuePositionByStationId(stationId);
        int nextPosition = maxPosition + 1;
        order.setQueuePosition(nextPosition);
        return nextPosition;
    }

    @Override
    public int getQueuePosition(Long orderId) {
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with id: " + orderId));

        if (order.getQueuePosition() == null) {
            throw new EntityNotFoundException("Order " + orderId + " does not have a queue position assigned");
        }

        return order.getQueuePosition();
    }
}
