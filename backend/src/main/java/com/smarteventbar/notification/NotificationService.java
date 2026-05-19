package com.smarteventbar.notification;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates outbound notifications across channels, triggered by order state transitions.
 * All notification methods are async and fire-and-forget from the order flow's perspective.
 * Failures are logged but never propagate to the caller.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String CHANNEL_WHATSAPP = "whatsapp";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SENT = "sent";
    private static final String STATUS_FAILED = "failed";
    private static final String MSG_TYPE_ORDER_CONFIRMED = "order_confirmed";
    private static final String MSG_TYPE_ORDER_READY = "order_ready";
    private static final String MSG_TYPE_ORDER_RECEIPT = "order_receipt";

    private static final List<String> DEDUP_STATUSES = Arrays.asList("sent", "delivered", "read");
    private static final List<String> VALID_DELIVERY_STATUSES = List.of("sent", "delivered", "read", "failed");
    private static final int MAX_SUPPORT_REPLY_ORDERS = 5;

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationChannel whatsAppChannel;
    private final OrderRepository orderRepository;

    /**
     * Constructor with Optional injection for the WhatsApp channel.
     * The channel may be absent if whatsapp.enabled=false (bean is conditionally created).
     */
    public NotificationService(NotificationLogRepository notificationLogRepository,
                               Optional<NotificationChannel> whatsAppChannel,
                               OrderRepository orderRepository) {
        this.notificationLogRepository = notificationLogRepository;
        this.whatsAppChannel = whatsAppChannel.orElse(null);
        this.orderRepository = orderRepository;
    }

    /**
     * Triggered asynchronously after order transitions to PAID.
     * Sends an order_confirmed WhatsApp notification if opt-in and channel enabled.
     */
    @Async("notificationExecutor")
    public void notifyOrderConfirmed(CustomerOrder order) {
        sendNotification(order, MSG_TYPE_ORDER_CONFIRMED);
    }

    /**
     * Triggered asynchronously after order transitions to READY.
     * Sends an order_ready WhatsApp notification if opt-in and channel enabled.
     */
    @Async("notificationExecutor")
    public void notifyOrderReady(CustomerOrder order) {
        sendNotification(order, MSG_TYPE_ORDER_READY);
    }

    /**
     * Triggered asynchronously after order transitions to COLLECTED.
     * Sends an order_receipt WhatsApp notification if opt-in and channel enabled.
     */
    @Async("notificationExecutor")
    public void notifyOrderCollected(CustomerOrder order) {
        sendNotification(order, MSG_TYPE_ORDER_RECEIPT);
    }

    /**
     * Process delivery status webhook callback.
     * Updates the corresponding notification log entry status.
     *
     * @param providerMessageId the message ID from the provider (e.g. wamid.xxx)
     * @param status the reported delivery status (sent, delivered, read, failed)
     */
    public void processDeliveryStatus(String providerMessageId, String status) {
        Optional<NotificationLog> logEntry = notificationLogRepository.findByProviderMessageId(providerMessageId);

        if (logEntry.isEmpty()) {
            log.warn("Received delivery status update for unknown provider message ID: {}", providerMessageId);
            return;
        }

        if (!VALID_DELIVERY_STATUSES.contains(status)) {
            log.warn("Received invalid delivery status '{}' for provider message ID: {}", status, providerMessageId);
            return;
        }

        NotificationLog notification = logEntry.get();
        notification.setStatus(status);
        notificationLogRepository.save(notification);

        log.debug("Updated notification log {} to status '{}' for provider message ID: {}",
                notification.getId(), status, providerMessageId);
    }

    /**
     * Process inbound customer message and send auto-reply with active order statuses.
     * Queries active orders (PAID, PREPARING, READY) by phone number and sends a support
     * reply containing up to 5 most recent orders with their visual order numbers and statuses.
     * If more than 5 active orders exist, the reply indicates older orders are present.
     *
     * This method is called synchronously from the webhook controller.
     * Exceptions from the channel are caught and logged (never propagated).
     *
     * @param phoneNumber the sender's phone number in E.164 format
     * @param messageBody the inbound message text
     */
    public void processInboundMessage(String phoneNumber, String messageBody) {
        log.info("Processing inbound message from phone={}", phoneNumber);

        List<CustomerOrder> activeOrders = notificationLogRepository.findActiveOrdersByPhone(phoneNumber);

        if (activeOrders.isEmpty()) {
            log.info("No active orders found for phone={}. No support reply sent.", phoneNumber);
            return;
        }

        if (whatsAppChannel == null || !whatsAppChannel.isEnabled()) {
            log.debug("WhatsApp channel is disabled, skipping support reply for phone={}", phoneNumber);
            return;
        }

        // Limit to the 5 most recent orders (repository already returns ordered by createdAt DESC)
        List<CustomerOrder> limitedOrders = activeOrders.size() > MAX_SUPPORT_REPLY_ORDERS
                ? activeOrders.subList(0, MAX_SUPPORT_REPLY_ORDERS)
                : activeOrders;

        if (activeOrders.size() > MAX_SUPPORT_REPLY_ORDERS) {
            log.info("Customer phone={} has {} active orders, replying with top {} and indicating more exist",
                    phoneNumber, activeOrders.size(), MAX_SUPPORT_REPLY_ORDERS);
        }

        try {
            String messageId = whatsAppChannel.sendSupportReply(phoneNumber, limitedOrders);
            log.info("Support reply sent to phone={} with {} orders listed (total active: {}), messageId: {}",
                    phoneNumber, limitedOrders.size(), activeOrders.size(), messageId);
        } catch (Exception e) {
            log.error("Failed to send support reply to phone={}: {}", phoneNumber, e.getMessage(), e);
        }
    }

    /**
     * Core notification dispatch logic shared by all notification methods.
     * Performs: opt-in check → channel check → deduplication → create PENDING log → send → update log.
     */
    private void sendNotification(CustomerOrder order, String messageType) {
        // 1. Check opt-in — skip silently if not opted in
        if (!order.isWhatsappOptIn()) {
            log.debug("Order {} has whatsappOptIn=false, skipping {} notification",
                    order.getId(), messageType);
            return;
        }

        // 2. Check channel availability — skip silently if disabled or absent
        if (whatsAppChannel == null || !whatsAppChannel.isEnabled()) {
            log.debug("WhatsApp channel is disabled or not configured, skipping {} notification for order {}",
                    messageType, order.getId());
            return;
        }

        // 3. Deduplication check — skip if already sent/delivered/read for this order + message type
        Optional<NotificationLog> existingLog = notificationLogRepository
                .findByOrderIdAndMessageTypeAndStatusIn(order.getId(), messageType, DEDUP_STATUSES);
        if (existingLog.isPresent()) {
            log.debug("Notification deduplicated: {} already sent for order {} (status: {})",
                    messageType, order.getId(), existingLog.get().getStatus());
            return;
        }

        // 4. Create PENDING log entry before sending
        NotificationLog logEntry = new NotificationLog();
        logEntry.setOrder(order);
        logEntry.setChannel(CHANNEL_WHATSAPP);
        logEntry.setMessageType(messageType);
        logEntry.setDestination(order.getCustomerPhone());
        logEntry.setStatus(STATUS_PENDING);
        logEntry = notificationLogRepository.save(logEntry);

        // 5. Delegate to channel and update log
        try {
            String providerMessageId = dispatchToChannel(order, messageType);
            logEntry.setProviderMessageId(providerMessageId);
            logEntry.setStatus(STATUS_SENT);
            notificationLogRepository.save(logEntry);
            log.info("Successfully sent {} notification for order {}, providerMessageId: {}",
                    messageType, order.getId(), providerMessageId);
        } catch (Exception e) {
            logEntry.setStatus(STATUS_FAILED);
            logEntry.setErrorMessage(e.getMessage());
            notificationLogRepository.save(logEntry);
            log.error("Failed to send {} notification for order {}: {}",
                    messageType, order.getId(), e.getMessage(), e);
        }
    }

    /**
     * Dispatches the notification to the appropriate channel method based on message type.
     */
    private String dispatchToChannel(CustomerOrder order, String messageType) {
        return switch (messageType) {
            case MSG_TYPE_ORDER_CONFIRMED -> whatsAppChannel.sendOrderConfirmed(order);
            case MSG_TYPE_ORDER_READY -> whatsAppChannel.sendOrderReady(order);
            case MSG_TYPE_ORDER_RECEIPT -> whatsAppChannel.sendReceipt(order);
            default -> throw new IllegalArgumentException("Unknown message type: " + messageType);
        };
    }
}
