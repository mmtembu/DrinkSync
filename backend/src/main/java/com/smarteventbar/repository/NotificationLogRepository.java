package com.smarteventbar.repository;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * Find notification log entries for deduplication check.
     * Used to prevent sending duplicate notifications for the same order and message type.
     */
    Optional<NotificationLog> findByOrderIdAndMessageTypeAndStatusIn(
            Long orderId, String messageType, List<String> statuses);

    /**
     * Find notification log entry by provider message ID.
     * Used for updating delivery status from webhook callbacks.
     */
    Optional<NotificationLog> findByProviderMessageId(String providerMessageId);

    /**
     * Find active orders by customer phone number.
     * Returns orders in PAID, PREPARING, or READY states for the given phone number.
     * Used for support auto-reply to list active orders.
     */
    @Query("SELECT o FROM CustomerOrder o WHERE o.customerPhone = :phone " +
           "AND o.state IN (com.smarteventbar.model.enums.OrderState.PAID, " +
           "com.smarteventbar.model.enums.OrderState.PREPARING, " +
           "com.smarteventbar.model.enums.OrderState.READY) " +
           "ORDER BY o.createdAt DESC")
    List<CustomerOrder> findActiveOrdersByPhone(@Param("phone") String phone);
}
