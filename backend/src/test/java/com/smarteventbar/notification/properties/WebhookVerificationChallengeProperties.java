package com.smarteventbar.notification.properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.controller.WebhookController;
import com.smarteventbar.notification.NotificationService;
import com.smarteventbar.notification.SignatureVerifier;
import net.jqwik.api.*;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import static org.mockito.Mockito.mock;

/**
 * Property-based tests for webhook verification challenge echo.
 *
 * Property 11: Webhook verification echoes challenge
 * For any string value provided as hub.challenge in a GET request with hub.mode = "subscribe"
 * and a matching hub.verify_token, the response body SHALL equal the challenge string exactly,
 * with HTTP status 200.
 *
 * Validates: Requirements 6.1
 */
class WebhookVerificationChallengeProperties {

    private static final String CONFIGURED_VERIFY_TOKEN = "test-verify-token-abc123";

    private final WhatsAppProperties whatsAppProperties;
    private final WebhookController webhookController;

    WebhookVerificationChallengeProperties() {
        this.whatsAppProperties = new WhatsAppProperties();
        this.whatsAppProperties.setVerifyToken(CONFIGURED_VERIFY_TOKEN);
        this.whatsAppProperties.setEnabled(true);
        this.whatsAppProperties.setAppSecret("test-secret");

        SignatureVerifier signatureVerifier = mock(SignatureVerifier.class);
        NotificationService notificationService = mock(NotificationService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        this.webhookController = new WebhookController(
                whatsAppProperties, signatureVerifier, notificationService, objectMapper);
    }

    @Provide
    Arbitrary<String> challengeStrings() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMinLength(1)
                .ofMaxLength(200);
    }

    /**
     * Property 11: For any ASCII alphanumeric challenge string, verifyWebhook with
     * mode="subscribe" and matching token returns HTTP 200 with the challenge as body.
     */
    @Property(tries = 100)
    void webhookVerificationEchoesChallenge(@ForAll("challengeStrings") String challenge) {
        ResponseEntity<String> response = webhookController.verifyWebhook(
                "subscribe", CONFIGURED_VERIFY_TOKEN, challenge);

        assert response.getStatusCode().value() == 200 :
                "Expected HTTP 200 but got " + response.getStatusCode().value();
        assert challenge.equals(response.getBody()) :
                "Expected response body to equal challenge '" + challenge
                        + "' but got '" + response.getBody() + "'";
    }
}
