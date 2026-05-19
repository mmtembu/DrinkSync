package com.smarteventbar.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.exception.WhatsAppApiException;
import com.smarteventbar.exception.WhatsAppRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Real HTTP implementation of {@link WhatsAppClient} that communicates with
 * the Meta WhatsApp Cloud API using Spring's RestClient.
 * <p>
 * This bean is only activated when the {@code whatsapp.access-token} property
 * is present AND not equal to "mock".
 */
@Component
@ConditionalOnExpression(
        "!'${whatsapp.access-token:}'.isEmpty() && '${whatsapp.access-token:}' != 'mock'"
)
public class MetaWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppClient.class);
    private static final String META_API_BASE_URL = "https://graph.facebook.com/v21.0";

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public MetaWhatsAppClient(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        Duration timeout = Duration.ofSeconds(properties.getSendTimeoutSeconds());
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(timeout)
                .withReadTimeout(timeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        this.restClient = RestClient.builder()
                .baseUrl(META_API_BASE_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .build();
    }

    /**
     * Package-private constructor for testing with a pre-built RestClient.
     */
    MetaWhatsAppClient(WhatsAppProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public WhatsAppResponse sendTemplateMessage(WhatsAppTemplateRequest request) {
        String endpoint = "/{phoneNumberId}/messages";

        log.debug("Sending WhatsApp template message to {}", request.getTo());

        return restClient.post()
                .uri(endpoint, properties.getPhoneNumberId())
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    handleErrorResponse(response.getStatusCode().value(), body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, response) -> {
                    String body = new String(response.getBody().readAllBytes());
                    handleErrorResponse(response.getStatusCode().value(), body);
                })
                .body(WhatsAppResponse.class);
    }

    /**
     * Parse error response body and throw the appropriate exception.
     * For HTTP 429, throws {@link WhatsAppRateLimitException} with Retry-After duration.
     * For all other errors, throws {@link WhatsAppApiException}.
     */
    private void handleErrorResponse(int statusCode, String responseBody) {
        String errorCode = "unknown";
        String errorMessage = responseBody;

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode()) {
                errorCode = errorNode.path("code").asText("unknown");
                errorMessage = errorNode.path("message").asText(responseBody);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse WhatsApp API error response body: {}", responseBody);
        }

        if (statusCode == 429) {
            Duration retryAfter = parseRetryAfter(responseBody);
            log.warn("WhatsApp API rate limit hit. Retry after: {}", retryAfter);
            throw new WhatsAppRateLimitException(
                    "Rate limited by WhatsApp API: " + errorMessage,
                    retryAfter
            );
        }

        log.error("WhatsApp API error: status={}, code={}, message={}", statusCode, errorCode, errorMessage);
        throw new WhatsAppApiException(errorMessage, statusCode, errorCode);
    }

    /**
     * Attempt to parse a Retry-After duration from the error response body or headers.
     * Falls back to 60 seconds if not parseable.
     */
    private Duration parseRetryAfter(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode errorNode = root.path("error");
            // Meta sometimes includes retry info in error_data
            JsonNode retryNode = errorNode.path("error_data").path("retry_after");
            if (!retryNode.isMissingNode()) {
                return Duration.ofSeconds(retryNode.asLong(60));
            }
        } catch (JsonProcessingException e) {
            // Fall through to default
        }
        return Duration.ofSeconds(60);
    }
}
