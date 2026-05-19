package com.smarteventbar.notification.properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.controller.WebhookController;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.notification.SignatureVerifier;
import net.jqwik.api.*;
import org.springframework.http.ResponseEntity;

import static org.mockito.Mockito.mock;

/**
 * Property-based tests for invalid verify token rejection.
 *
 * Property 12: Invalid verify token is rejected
 * For any string that does not equal the configured verification token, a GET verification
 * request SHALL receive HTTP 403 Forbidden.
 *
 * Validates: Requirements 6.2
 */
class InvalidVerifyTokenProperties {

    private static final String CONFIGURED_VERIFY_TOKEN = "test-verify-token-abc123";

    private final WebhookController webhookController;

    InvalidVerifyTokenProperties() {
        WhatsAppProperties whatsAppProperties = new WhatsAppProperties();
        whatsAppProperties.setVerifyToken(CONFIGURED_VERIFY_TOKEN);
        whatsAppProperties.setEnabled(true);
        whatsAppProperties.setAppSecret("test-secret");

        SignatureVerifier signatureVerifier = mock(SignatureVerifier.class);
        NotificationService notificationService = mock(NotificationService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        this.webhookController = new WebhookController(
                whatsAppProperties, signatureVerifier, notificationService, objectMapper);
    }

    @Provide
    Arbitrary<String> invalidTokens() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars('-', '_', '.')
                .ofMinLength(1)
                .ofMaxLength(100)
                .filter(token -> !token.equals(CONFIGURED_VERIFY_TOKEN));
    }

    /**
     * Property 12: For any string that does not equal the configured verification token,
     * verifyWebhook with mode="subscribe" returns HTTP 403 Forbidden.
     */
    @Property(tries = 100)
    void invalidVerifyTokenIsRejected(@ForAll("invalidTokens") String invalidToken) {
        ResponseEntity<String> response = webhookController.verifyWebhook(
                "subscribe", invalidToken, "any-challenge");

        assert response.getStatusCode().value() == 403 :
                "Expected HTTP 403 for invalid token '" + invalidToken
                        + "' but got " + response.getStatusCode().value();
    }
}
