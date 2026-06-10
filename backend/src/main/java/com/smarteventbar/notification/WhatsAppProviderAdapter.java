package com.smarteventbar.notification;

import com.smarteventbar.config.RateLimitedSendQueue;
import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.exception.WhatsAppRateLimitException;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ComponentObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.LanguageObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ParameterObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.TemplateObject;
import com.smarteventbar.model.entity.CustomerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WhatsApp implementation of the NotificationChannel interface.
 * Builds template message requests for each notification type and delegates
 * to the WhatsAppClient via a rate-limited send queue.
 *
 * <p>Only activated when {@code whatsapp.enabled=true} in application configuration.</p>
 */
@Component
@ConditionalOnProperty(name = "whatsapp.enabled", havingValue = "true", matchIfMissing = false)
public class WhatsAppProviderAdapter implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppProviderAdapter.class);

    private static final String LANGUAGE_CODE = "en";
    private static final String TEMPLATE_ORDER_CONFIRMED = "order_confirmed";
    private static final String TEMPLATE_ORDER_READY = "order_ready";
    private static final String TEMPLATE_ORDER_RECEIPT = "order_receipt";
    private static final String TEMPLATE_SUPPORT_REPLY = "support_reply";

    private final WhatsAppClient whatsAppClient;
    private final RateLimitedSendQueue rateLimitedSendQueue;

    public WhatsAppProviderAdapter(WhatsAppClient whatsAppClient, RateLimitedSendQueue rateLimitedSendQueue) {
        this.whatsAppClient = whatsAppClient;
        this.rateLimitedSendQueue = rateLimitedSendQueue;
    }

    @Override
    public String sendOrderConfirmed(CustomerOrder order) {
        log.debug("Sending order_confirmed notification for order {}", order.getVisualOrderNumber());

        List<ParameterObject> parameters = List.of(
                new ParameterObject("text", getCustomerName(order)),
                new ParameterObject("text", order.getVisualOrderNumber()),
                new ParameterObject("text", order.getStation().getName())
        );

        WhatsAppTemplateRequest request = buildTemplateRequest(
                order.getCustomerPhone(),
                TEMPLATE_ORDER_CONFIRMED,
                parameters
        );

        return sendWithRateLimit(request);
    }

    @Override
    public String sendOrderReady(CustomerOrder order) {
        log.debug("Sending order_ready notification for order {}", order.getVisualOrderNumber());

        List<ParameterObject> parameters = List.of(
                new ParameterObject("text", order.getVisualOrderNumber()),
                new ParameterObject("text", order.getStation().getName())
        );

        WhatsAppTemplateRequest request = buildTemplateRequest(
                order.getCustomerPhone(),
                TEMPLATE_ORDER_READY,
                parameters
        );

        return sendWithRateLimit(request);
    }

    @Override
    public String sendReceipt(CustomerOrder order) {
        log.debug("Sending order_receipt notification for order {}", order.getVisualOrderNumber());

        String businessName = order.getStation().getName();
        String receiptUrl = buildReceiptUrl(order);

        List<ParameterObject> parameters = List.of(
                new ParameterObject("text", businessName),
                new ParameterObject("text", order.getVisualOrderNumber()),
                new ParameterObject("text", order.getTotalPrice().toPlainString()),
                new ParameterObject("text", receiptUrl)
        );

        WhatsAppTemplateRequest request = buildTemplateRequest(
                order.getCustomerPhone(),
                TEMPLATE_ORDER_RECEIPT,
                parameters
        );

        return sendWithRateLimit(request);
    }

    @Override
    public String sendSupportReply(String phoneNumber, List<CustomerOrder> activeOrders) {
        log.debug("Sending support_reply notification to {}", phoneNumber);

        List<ParameterObject> parameters = new ArrayList<>();
        parameters.add(new ParameterObject("text", formatActiveOrders(activeOrders)));

        WhatsAppTemplateRequest request = buildTemplateRequest(
                phoneNumber,
                TEMPLATE_SUPPORT_REPLY,
                parameters
        );

        return sendWithRateLimit(request);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Builds a WhatsAppTemplateRequest with the given recipient, template name, and body parameters.
     */
    private WhatsAppTemplateRequest buildTemplateRequest(String recipientPhone, String templateName,
                                                         List<ParameterObject> parameters) {
        LanguageObject language = new LanguageObject(LANGUAGE_CODE);
        ComponentObject bodyComponent = new ComponentObject("body", parameters);
        TemplateObject template = new TemplateObject(templateName, language, List.of(bodyComponent));
        return new WhatsAppTemplateRequest(recipientPhone, template);
    }

    /**
     * Sends the template request through the rate-limited queue, acquiring a token first.
     * If a rate limit response (HTTP 429) is received, pauses the queue for the
     * Retry-After duration before re-throwing the exception.
     *
     * @return the provider message ID from the response
     * @throws WhatsAppRateLimitException if Meta returns HTTP 429 (after pausing the queue)
     */
    private String sendWithRateLimit(WhatsAppTemplateRequest request) {
        rateLimitedSendQueue.acquireToken();
        try {
            WhatsAppResponse response = whatsAppClient.sendTemplateMessage(request);
            return extractMessageId(response);
        } catch (WhatsAppRateLimitException e) {
            // Pause the queue for the duration specified in the Retry-After header
            rateLimitedSendQueue.pauseForDuration(e.getRetryAfter());
            throw e;
        }
    }

    /**
     * Extracts the provider message ID from the WhatsApp API response.
     */
    private String extractMessageId(WhatsAppResponse response) {
        if (response != null && response.getMessages() != null && !response.getMessages().isEmpty()) {
            return response.getMessages().get(0).getId();
        }
        return null;
    }

    /**
     * Gets the customer name from the order. Falls back to "Customer" if not available.
     */
    private String getCustomerName(CustomerOrder order) {
        // The session doesn't have a customer name field, so we use a default
        // In a future iteration, this could be populated from the checkout form
        return "Customer";
    }

    /**
     * Builds a receipt URL for the given order.
     */
    private String buildReceiptUrl(CustomerOrder order) {
        return "/orders/" + order.getVisualOrderNumber() + "/receipt";
    }

    /**
     * Formats active orders as a text string for the support reply template.
     * Shows order number and status for each order.
     */
    private String formatActiveOrders(List<CustomerOrder> activeOrders) {
        if (activeOrders == null || activeOrders.isEmpty()) {
            return "No active orders found.";
        }

        return activeOrders.stream()
                .map(order -> String.format("#%s - %s",
                        order.getVisualOrderNumber(),
                        order.getState().name()))
                .collect(Collectors.joining(", "));
    }
}
