package com.smarteventbar.service;

import com.smarteventbar.model.entity.CustomerOrder;

/**
 * Manages queue positions per station.
 * Queue positions are assigned monotonically based on payment confirmation order.
 */
public interface QueueService {

    /**
     * Assigns the next queue position for the given order's station.
     * The position is determined by incrementing the current maximum queue position
     * for the station. If no orders have a queue position yet, starts at 1.
     * This operation is thread-safe for concurrent payments at the same station.
     *
     * @param order the order to assign a queue position to
     * @return the assigned queue position
     */
    int assignQueuePosition(CustomerOrder order);

    /**
     * Returns the queue position for the given order.
     *
     * @param orderId the ID of the order
     * @return the queue position
     * @throws jakarta.persistence.EntityNotFoundException if the order is not found
     */
    int getQueuePosition(Long orderId);
}
