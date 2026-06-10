package com.smarteventbar.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.exception.WhatsAppApiException;
import com.smarteventbar.exception.WhatsAppRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link MetaWhatsAppClient}.
 * Uses MockRestServiceServer to verify HTTP request construction and error handling.
 */
class MetaWhatsAppClientTest {

    private MetaWhatsAppClient client;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        WhatsAppProperties properties = new WhatsAppProperties();
        properties.setPhoneNumberId("123456789");
        properties.setAccessToken("test-access-token");
        properties.setSendTimeoutSeconds(10);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://graph.facebook.com/v21.0");
        mockServer = MockRestServiceServer.bindTo(builder).build();

        client = new MetaWhatsAppClient(properties, objectMapper, builder.build());
    }

    @Test
    void sendTemplateMessage_success_returnsResponse() {
        String responseJson = """
                {
                    "messaging_product": "whatsapp",
                    "contacts": [{"input": "+27123456789", "wa_id": "27123456789"}],
                    "messages": [{"id": "wamid.HBgNMjc4MzE1NTU1NTU1FQIAERgSM"}]
                }
                """;

        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        WhatsAppTemplateRequest request = createSampleRequest();
        WhatsAppResponse response = client.sendTemplateMessage(request);

        assertNotNull(response);
        assertEquals("whatsapp", response.getMessagingProduct());
        assertEquals(1, response.getMessages().size());
        assertEquals("wamid.HBgNMjc4MzE1NTU1NTU1FQIAERgSM", response.getMessages().get(0).getId());
        mockServer.verify();
    }

    @Test
    void sendTemplateMessage_4xxError_throwsWhatsAppApiException() {
        String errorJson = """
                {
                    "error": {
                        "message": "Invalid phone number",
                        "type": "OAuthException",
                        "code": "100",
                        "fbtrace_id": "abc123"
                    }
                }
                """;

        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson));

        WhatsAppTemplateRequest request = createSampleRequest();

        WhatsAppApiException exception = assertThrows(WhatsAppApiException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(400, exception.getHttpStatus());
        assertEquals("100", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid phone number"));
        mockServer.verify();
    }

    @Test
    void sendTemplateMessage_5xxError_throwsWhatsAppApiException() {
        String errorJson = """
                {
                    "error": {
                        "message": "Internal server error",
                        "type": "ServerException",
                        "code": "2",
                        "fbtrace_id": "xyz789"
                    }
                }
                """;

        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson));

        WhatsAppTemplateRequest request = createSampleRequest();

        WhatsAppApiException exception = assertThrows(WhatsAppApiException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(500, exception.getHttpStatus());
        assertEquals("2", exception.getErrorCode());
        mockServer.verify();
    }

    @Test
    void sendTemplateMessage_429RateLimit_throwsWhatsAppRateLimitException() {
        String errorJson = """
                {
                    "error": {
                        "message": "Too many requests",
                        "type": "OAuthException",
                        "code": "4",
                        "error_data": {
                            "retry_after": 30
                        }
                    }
                }
                """;

        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson));

        WhatsAppTemplateRequest request = createSampleRequest();

        WhatsAppRateLimitException exception = assertThrows(WhatsAppRateLimitException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(429, exception.getHttpStatus());
        assertEquals(30, exception.getRetryAfter().getSeconds());
        mockServer.verify();
    }

    @Test
    void sendTemplateMessage_429WithoutRetryAfter_defaultsTo60Seconds() {
        String errorJson = """
                {
                    "error": {
                        "message": "Rate limit exceeded",
                        "type": "OAuthException",
                        "code": "4"
                    }
                }
                """;

        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson));

        WhatsAppTemplateRequest request = createSampleRequest();

        WhatsAppRateLimitException exception = assertThrows(WhatsAppRateLimitException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(60, exception.getRetryAfter().getSeconds());
        mockServer.verify();
    }

    @Test
    void sendTemplateMessage_malformedErrorBody_stillThrowsException() {
        mockServer.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("not valid json"));

        WhatsAppTemplateRequest request = createSampleRequest();

        WhatsAppApiException exception = assertThrows(WhatsAppApiException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(400, exception.getHttpStatus());
        assertEquals("unknown", exception.getErrorCode());
        mockServer.verify();
    }

    private WhatsAppTemplateRequest createSampleRequest() {
        WhatsAppTemplateRequest.LanguageObject language = new WhatsAppTemplateRequest.LanguageObject("en");
        WhatsAppTemplateRequest.ParameterObject param = new WhatsAppTemplateRequest.ParameterObject("text", "John");
        WhatsAppTemplateRequest.ComponentObject component = new WhatsAppTemplateRequest.ComponentObject("body", List.of(param));
        WhatsAppTemplateRequest.TemplateObject template = new WhatsAppTemplateRequest.TemplateObject(
                "order_confirmed", language, List.of(component));
        return new WhatsAppTemplateRequest("+27123456789", template);
    }
}
