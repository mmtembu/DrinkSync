package com.smarteventbar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.notification.SignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookController")
class WebhookControllerTest {

    @Mock
    private WhatsAppProperties whatsAppProperties;

    @Mock
    private SignatureVerifier signatureVerifier;

    @Mock
    private NotificationService notificationService;

    private WebhookController controller;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        controller = new WebhookController(whatsAppProperties, signatureVerifier, notificationService, objectMapper);
    }

    @Nested
    @DisplayName("GET /api/webhooks/whatsapp - Verification")
    class VerifyWebhookTests {

        @Test
        @DisplayName("Should return 200 with challenge when mode is subscribe and token matches")
        void verifyWebhook_validRequest_returnsChallenge() {
            when(whatsAppProperties.getVerifyToken()).thenReturn("my-secret-token");

            ResponseEntity<String> response = controller.verifyWebhook("subscribe", "my-secret-token", "challenge123");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("challenge123", response.getBody());
        }

        @Test
        @DisplayName("Should return 403 when verify token does not match")
        void verifyWebhook_invalidToken_returns403() {
            when(whatsAppProperties.getVerifyToken()).thenReturn("my-secret-token");

            ResponseEntity<String> response = controller.verifyWebhook("subscribe", "wrong-token", "challenge123");

            assertEquals(403, response.getStatusCode().value());
            assertNull(response.getBody());
        }

        @Test
        @DisplayName("Should return 403 when mode is not subscribe")
        void verifyWebhook_invalidMode_returns403() {
            ResponseEntity<String> response = controller.verifyWebhook("unsubscribe", "my-secret-token", "challenge123");

            assertEquals(403, response.getStatusCode().value());
            assertNull(response.getBody());
        }

        @Test
        @DisplayName("Should return challenge exactly as provided")
        void verifyWebhook_echoesExactChallenge() {
            when(whatsAppProperties.getVerifyToken()).thenReturn("token");

            String challenge = "a complex challenge with spaces & special chars!";
            ResponseEntity<String> response = controller.verifyWebhook("subscribe", "token", challenge);

            assertEquals(200, response.getStatusCode().value());
            assertEquals(challenge, response.getBody());
        }
    }

    @Nested
    @DisplayName("POST /api/webhooks/whatsapp - Webhook handling")
    class HandleWebhookTests {

        @Test
        @DisplayName("Should return 401 when X-Hub-Signature-256 header is missing")
        void handleWebhook_missingSignature_returns401() {
            ResponseEntity<Void> response = controller.handleWebhook(null, "{\"object\":\"whatsapp_business_account\"}");

            assertEquals(401, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should return 401 when X-Hub-Signature-256 header is blank")
        void handleWebhook_blankSignature_returns401() {
            ResponseEntity<Void> response = controller.handleWebhook("   ", "{\"object\":\"whatsapp_business_account\"}");

            assertEquals(401, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should return 401 when signature verification fails")
        void handleWebhook_invalidSignature_returns401() {
            String body = "{\"object\":\"whatsapp_business_account\"}";
            when(signatureVerifier.verify(body, "sha256=invalid")).thenReturn(false);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=invalid", body);

            assertEquals(401, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should return 200 and process delivery status update")
        void handleWebhook_deliveryStatus_returns200AndDelegates() {
            String body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [{
                          "id": "wamid.abc123",
                          "status": "delivered"
                        }]
                      }
                    }]
                  }]
                }
                """;
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verify(notificationService).processDeliveryStatus("wamid.abc123", "delivered");
        }

        @Test
        @DisplayName("Should return 200 and process inbound message")
        void handleWebhook_inboundMessage_returns200AndDelegates() {
            String body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "+27821234567",
                          "text": {"body": "Where is my order?"}
                        }]
                      }
                    }]
                  }]
                }
                """;
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verify(notificationService).processInboundMessage("+27821234567", "Where is my order?");
        }

        @Test
        @DisplayName("Should return 200 even when payload parsing fails")
        void handleWebhook_malformedPayload_returns200() {
            String body = "not valid json at all";
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should handle payload with no entry array gracefully")
        void handleWebhook_noEntryArray_returns200() {
            String body = "{\"object\":\"whatsapp_business_account\"}";
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should handle multiple statuses in a single payload")
        void handleWebhook_multipleStatuses_processesAll() {
            String body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [
                          {"id": "wamid.001", "status": "sent"},
                          {"id": "wamid.002", "status": "delivered"}
                        ]
                      }
                    }]
                  }]
                }
                """;
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verify(notificationService).processDeliveryStatus("wamid.001", "sent");
            verify(notificationService).processDeliveryStatus("wamid.002", "delivered");
        }

        @Test
        @DisplayName("Should skip status entries missing id or status fields")
        void handleWebhook_incompleteStatusEntry_skipsGracefully() {
            String body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "statuses": [
                          {"status": "delivered"},
                          {"id": "wamid.001"}
                        ]
                      }
                    }]
                  }]
                }
                """;
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("Should handle inbound message without text body")
        void handleWebhook_messageWithoutTextBody_delegatesWithEmptyBody() {
            String body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "+27821234567",
                          "type": "image"
                        }]
                      }
                    }]
                  }]
                }
                """;
            when(signatureVerifier.verify(body, "sha256=validhash")).thenReturn(true);

            ResponseEntity<Void> response = controller.handleWebhook("sha256=validhash", body);

            assertEquals(200, response.getStatusCode().value());
            verify(notificationService).processInboundMessage("+27821234567", "");
        }
    }
}
