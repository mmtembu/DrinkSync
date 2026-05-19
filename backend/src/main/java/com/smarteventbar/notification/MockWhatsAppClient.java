package com.smarteventbar.notification;

import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.exception.WhatsAppApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Development mock implementation of {@link WhatsAppClient} that simulates
 * the Meta WhatsApp Cloud API without making real HTTP requests.
 * <p>
 * Activated when the {@code whatsapp.access-token} property is set to "mock"
 * or is empty/absent. Logs all outbound messages at INFO level and returns
 * simulated successful responses with generated UUIDs as provider message IDs.
 * <p>
 * Supports a configurable failure rate via the {@code whatsapp.mock.failure-rate-percent}
 * property (default 0%) to allow testing of error handling paths.
 */
@Component
@ConditionalOnExpression(
        "'${whatsapp.access-token:}'.isEmpty() || '${whatsapp.access-token:}' == 'mock'"
)
public class MockWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppClient.class);

    private final int failureRatePercent;

    public MockWhatsAppClient(
            @Value("${whatsapp.mock.failure-rate-percent:0}") int failureRatePercent) {
        this.failureRatePercent = failureRatePercent;
        log.info("MockWhatsAppClient activated (failure-rate={}%)", failureRatePercent);
    }

    @Override
    public WhatsAppResponse sendTemplateMessage(WhatsAppTemplateRequest request) {
        String destination = request.getTo();
        String templateName = extractTemplateName(request);
        String parameters = extractParameters(request);

        log.info("[MOCK] WhatsApp message: destination={}, template={}, parameters=[{}]",
                destination, templateName, parameters);

        if (shouldSimulateFailure()) {
            log.info("[MOCK] Simulating WhatsApp API failure for destination={}", destination);
            throw new WhatsAppApiException(
                    "Mock simulated failure",
                    500,
                    "mock_error"
            );
        }

        String messageId = "wamid." + UUID.randomUUID();

        WhatsAppResponse.Contact contact = new WhatsAppResponse.Contact(destination, destination);
        WhatsAppResponse.Message message = new WhatsAppResponse.Message(messageId);

        WhatsAppResponse response = new WhatsAppResponse(
                "whatsapp",
                List.of(contact),
                List.of(message)
        );

        log.info("[MOCK] WhatsApp message sent successfully: messageId={}", messageId);
        return response;
    }

    private String extractTemplateName(WhatsAppTemplateRequest request) {
        if (request.getTemplate() != null) {
            return request.getTemplate().getName();
        }
        return "unknown";
    }

    private String extractParameters(WhatsAppTemplateRequest request) {
        if (request.getTemplate() == null || request.getTemplate().getComponents() == null) {
            return "";
        }
        return request.getTemplate().getComponents().stream()
                .filter(c -> c.getParameters() != null)
                .flatMap(c -> c.getParameters().stream())
                .map(WhatsAppTemplateRequest.ParameterObject::getText)
                .collect(Collectors.joining(", "));
    }

    private boolean shouldSimulateFailure() {
        if (failureRatePercent <= 0) {
            return false;
        }
        if (failureRatePercent >= 100) {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < failureRatePercent;
    }
}
