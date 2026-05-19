package com.smarteventbar.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.notification.SignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller handling inbound webhook events from Meta's WhatsApp Cloud API.
 * <p>
 * Provides two endpoints:
 * <ul>
 *   <li>GET /api/webhooks/whatsapp — Meta webhook verification handshake</li>
 *   <li>POST /api/webhooks/whatsapp — Inbound delivery status updates and messages</li>
 * </ul>
 * <p>
 * This controller is excluded from the Spring Security / JWT authentication chain.
 * The JwtAuthFilter only intercepts vendor-protected routes, so this endpoint is
 * inherently unauthenticated. POST requests are secured via HMAC-SHA256 signature
 * verification using the Meta App Secret.
 * <p>
 * Validates: Requirements 6.1, 6.2, 6.3, 7.1, 7.4, 7.5, 8.1, 14.1, 14.4, 14.5
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppProperties whatsAppProperties;
    private final SignatureVerifier signatureVerifier;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public WebhookController(WhatsAppProperties whatsAppProperties,
                             SignatureVerifier signatureVerifier,
                             NotificationService notificationService,
                             ObjectMapper objectMapper) {
        this.whatsAppProperties = whatsAppProperties;
        this.signatureVerifier = signatureVerifier;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET - Meta webhook verification handshake.
     * <p>
     * Meta sends a GET request with hub.mode, hub.verify_token, and hub.challenge.
     * If hub.mode is "subscribe" and hub.verify_token matches the configured token,
     * respond with 200 and the hub.challenge value. Otherwise respond with 403.
     * <p>
     * No authentication required (per Meta protocol).
     *
     * @param mode        the hub.mode parameter (should be "subscribe")
     * @param verifyToken the hub.verify_token parameter to validate
     * @param challenge   the hub.challenge string to echo back
     * @return 200 with challenge body on success, 403 on failure
     */
    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && whatsAppProperties.getVerifyToken().equals(verifyToken)) {
            log.info("Webhook verification successful, returning challenge");
            return ResponseEntity.ok(challenge);
        }

        log.warn("Webhook verification failed: mode='{}', token mismatch", mode);
        return ResponseEntity.status(403).build();
    }

    /**
     * POST - Inbound webhook events (delivery status updates, inbound messages).
     * <p>
     * Verifies the X-Hub-Signature-256 header against the raw request body using
     * HMAC-SHA256 with the configured App Secret. Returns 401 if the signature is
     * missing or invalid.
     * <p>
     * Per Meta's protocol, all valid POST requests receive a 200 response regardless
     * of internal processing outcome.
     *
     * @param signature the X-Hub-Signature-256 header value (may be null if missing)
     * @param rawBody   the raw request body as a string
     * @return 200 for valid requests, 401 for missing/invalid signature
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {

        // Verify signature — return 401 if missing or invalid
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook POST received without X-Hub-Signature-256 header");
            return ResponseEntity.status(401).build();
        }

        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Webhook POST signature verification failed");
            return ResponseEntity.status(401).build();
        }

        // Parse and process the payload
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            processWebhookPayload(payload);
        } catch (Exception e) {
            // Per Meta protocol, always return 200 even if processing fails internally
            log.error("Error processing webhook payload: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Parses the webhook payload and delegates to the appropriate handler.
     * Handles both delivery status updates and inbound messages.
     */
    private void processWebhookPayload(JsonNode payload) {
        JsonNode entryArray = payload.path("entry");
        if (!entryArray.isArray()) {
            log.debug("Webhook payload has no 'entry' array, ignoring");
            return;
        }

        for (JsonNode entry : entryArray) {
            JsonNode changesArray = entry.path("changes");
            if (!changesArray.isArray()) {
                continue;
            }

            for (JsonNode change : changesArray) {
                JsonNode value = change.path("value");
                if (value.isMissingNode()) {
                    continue;
                }

                // Handle delivery status updates
                JsonNode statuses = value.path("statuses");
                if (statuses.isArray()) {
                    for (JsonNode status : statuses) {
                        processDeliveryStatus(status);
                    }
                }

                // Handle inbound messages
                JsonNode messages = value.path("messages");
                if (messages.isArray()) {
                    for (JsonNode message : messages) {
                        processInboundMessage(message);
                    }
                }
            }
        }
    }

    /**
     * Processes a single delivery status update from the webhook payload.
     */
    private void processDeliveryStatus(JsonNode statusNode) {
        String messageId = statusNode.path("id").asText(null);
        String status = statusNode.path("status").asText(null);

        if (messageId == null || status == null) {
            log.debug("Delivery status update missing id or status, ignoring");
            return;
        }

        log.debug("Processing delivery status: messageId={}, status={}", messageId, status);
        notificationService.processDeliveryStatus(messageId, status);
    }

    /**
     * Processes a single inbound message from the webhook payload.
     */
    private void processInboundMessage(JsonNode messageNode) {
        String from = messageNode.path("from").asText(null);
        String body = messageNode.path("text").path("body").asText(null);

        if (from == null) {
            log.debug("Inbound message missing 'from' field, ignoring");
            return;
        }

        // Default to empty string if no text body (could be media message)
        if (body == null) {
            body = "";
        }

        log.debug("Processing inbound message from: {}", from);
        notificationService.processInboundMessage(from, body);
    }
}
