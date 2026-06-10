package com.smarteventbar.notification;

import com.smarteventbar.model.entity.CustomerOrder;

import java.util.List;

/**
 * Contract for notification channels. Implementations handle the delivery
 * of order lifecycle messages to customers via a specific provider (e.g. WhatsApp).
 */
public interface NotificationChannel {

    /**
     * Send order confirmed notification.
     *
     * @param order the confirmed order
     * @return provider message ID on success
     */
    String sendOrderConfirmed(CustomerOrder order);

    /**
     * Send order ready for pickup notification.
     *
     * @param order the ready order
     * @return provider message ID on success
     */
    String sendOrderReady(CustomerOrder order);

    /**
     * Send order receipt notification.
     *
     * @param order the collected order
     * @return provider message ID on success
     */
    String sendReceipt(CustomerOrder order);

    /**
     * Send support auto-reply with active order statuses.
     *
     * @param phoneNumber the customer's phone number in E.164 format
     * @param activeOrders the customer's active orders
     * @return provider message ID on success
     */
    String sendSupportReply(String phoneNumber, List<CustomerOrder> activeOrders);

    /**
     * Whether this channel is currently enabled and configured.
     *
     * @return true if the channel can send messages
     */
    boolean isEnabled();
}
