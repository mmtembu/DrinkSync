package com.smarteventbar.integration;

import com.smarteventbar.model.entity.CustomerOrder;
import com.smarteventbar.model.entity.CustomerSession;
import com.smarteventbar.model.entity.NotificationLog;
import com.smarteventbar.model.entity.Station;
import com.smarteventbar.model.enums.OrderState;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.repository.CustomerSessionRepository;
import com.smarteventbar.repository.NotificationLogRepository;
import com.smarteventbar.repository.OrderRepository;
import com.smarteventbar.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for webhook processing endpoints.
 * <p>
 * Uses RANDOM_PORT to start the full server and TestRestTemplate for HTTP requests.
 * Tests delivery status updates, inbound messages, and signature verification.
 * <p>
 * Uses the "test" profile which configures Testcontainers JDBC URL for PostgreSQL.
 * <p>
 * Validates: Requirements 5.5, 7.2, 8.2, 14.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebhookIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private CustomerSessionRepository customerSessionRepository;

    @Autowired
    private NotificationService notificationService;

    @Value("${whatsapp.verify-token}")
    private String verifyToken;

    @Value("${whatsapp.app-secret}")
    private String appSecret;

    private CustomerOrder testOrder;

    @BeforeEach
    void setUp() {
        Station station = stationRepository.save(
                new Station("Webhook Test Bar", "VIP Area", new BigDecimal("5.00"),
                        UUID.randomUUID().toString().substring(0, 6).toUpperCase(), 10));

        CustomerSession session = new CustomerSession(UUID.randomUUID().toString(), station);
        session = customerSessionRepository.save(session);

        testOrder = new CustomerOrder(station, session);
        testOrder.setCustomerPhone("+27829876543");
        testOrder.setWhatsappOptIn(true);
        testOrder.setState(OrderState.PAID);
        testOrder.setVisualOrderNumber("ORD-WH-" + UUID.randomUUID().toString().substring(0, 4));
        testOrder = orderRepository.save(testOrder);
    }

    // -----------------------------------------------------------------------
    // Webhook Verification (GET)
    // -----------------------------------------------------------------------

    @Test
    void verifyWebhook_validToken_returns200WithChallenge() {
        String challenge = "test_challenge_" + UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token={token}&hub.challenge={challenge}",
                String.class, verifyToken, challenge);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(challenge, response.getBody());
    }

    @Test
    void verifyWebhook_invalidToken_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token={token}&hub.challenge={challenge}",
                String.class, "wrong-token", "some-challenge");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void verifyWebhook_invalidMode_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/webhooks/whatsapp?hub.mode=unsubscribe&hub.verify_token={token}&hub.challenge={challenge}",
                String.class, verifyToken, "some-challenge");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Delivery Status Update (POST)
    // -----------------------------------------------------------------------

    @Test
    void handleWebhook_deliveryStatusUpdate_updatesLogEntry() {
        // First, create a notification log entry with a known provider message ID
        NotificationLog logEntry = new NotificationLog();
        logEntry.setOrder(testOrder);
        logEntry.setChannel("whatsapp");
        logEntry.setMessageType("order_confirmed");
        logEntry.setDestination("+27829876543");
        logEntry.setProviderMessageId("wamid.delivery-test-123");
        logEntry.setStatus("sent");
        logEntry.setSentAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        logEntry = notificationLogRepository.save(logEntry);

        // Build delivery status webhook payload
        String payload = buildDeliveryStatusPayload("wamid.delivery-test-123", "delivered");

        // Send POST with valid signature
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeSignature(payload));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify the log entry was updated
        NotificationLog updated = notificationLogRepository.findById(logEntry.getId()).orElseThrow();
        assertEquals("delivered", updated.getStatus());
    }

    // -----------------------------------------------------------------------
    // Inbound Message (POST)
    // -----------------------------------------------------------------------

    @Test
    void handleWebhook_inboundMessage_returns200() {
        // Build inbound message payload
        String payload = buildInboundMessagePayload("+27829876543", "What is my order status?");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeSignature(payload));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Signature Verification (POST)
    // -----------------------------------------------------------------------

    @Test
    void handleWebhook_missingSignature_returns401() {
        String payload = buildDeliveryStatusPayload("wamid.test", "delivered");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No X-Hub-Signature-256 header

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void handleWebhook_invalidSignature_returns401() {
        String payload = buildDeliveryStatusPayload("wamid.test", "delivered");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", "sha256=invalid_signature_value");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void handleWebhook_validSignatureWithMalformedPayload_returns200() {
        // Per Meta protocol, always return 200 for valid signatures even if payload is malformed
        String payload = "{\"invalid\": \"payload\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Hub-Signature-256", computeSignature(payload));

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void handleWebhook_signatureWithWrongPrefix_returns401() {
        String payload = buildDeliveryStatusPayload("wamid.test", "delivered");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Wrong prefix (sha1 instead of sha256)
        headers.set("X-Hub-Signature-256", "sha1=somehashvalue");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/webhooks/whatsapp",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Void.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // Helper Methods
    // -----------------------------------------------------------------------

    private String buildDeliveryStatusPayload(String messageId, String status) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "123456",
                    "changes": [{
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "27000000000",
                          "phone_number_id": "test-phone-id"
                        },
                        "statuses": [{
                          "id": "%s",
                          "status": "%s",
                          "timestamp": "1234567890",
                          "recipient_id": "27829876543"
                        }]
                      },
                      "field": "messages"
                    }]
                  }]
                }
                """.formatted(messageId, status);
    }

    private String buildInboundMessagePayload(String from, String body) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "123456",
                    "changes": [{
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "27000000000",
                          "phone_number_id": "test-phone-id"
                        },
                        "messages": [{
                          "from": "%s",
                          "id": "wamid.inbound123",
                          "timestamp": "1234567890",
                          "text": {
                            "body": "%s"
                          },
                          "type": "text"
                        }]
                      },
                      "field": "messages"
                    }]
                  }]
                }
                """.formatted(from, body);
    }

    private String computeSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
