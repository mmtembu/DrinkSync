package com.smarteventbar.notification;

import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ComponentObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.LanguageObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ParameterObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.TemplateObject;
import com.smarteventbar.exception.WhatsAppApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockWhatsAppClientTest {

    @Test
    void sendTemplateMessage_withZeroFailureRate_returnsSuccessfulResponse() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        WhatsAppTemplateRequest request = createSampleRequest("+27821234567", "order_confirmed");

        WhatsAppResponse response = client.sendTemplateMessage(request);

        assertNotNull(response);
        assertEquals("whatsapp", response.getMessagingProduct());
        assertNotNull(response.getContacts());
        assertEquals(1, response.getContacts().size());
        assertEquals("+27821234567", response.getContacts().get(0).getInput());
        assertNotNull(response.getMessages());
        assertEquals(1, response.getMessages().size());
        assertTrue(response.getMessages().get(0).getId().startsWith("wamid."));
    }

    @Test
    void sendTemplateMessage_generatesUniqueMessageIds() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        WhatsAppTemplateRequest request = createSampleRequest("+27821234567", "order_confirmed");

        WhatsAppResponse response1 = client.sendTemplateMessage(request);
        WhatsAppResponse response2 = client.sendTemplateMessage(request);

        assertNotEquals(
                response1.getMessages().get(0).getId(),
                response2.getMessages().get(0).getId()
        );
    }

    @Test
    void sendTemplateMessage_messageIdHasWamidPrefix() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        WhatsAppTemplateRequest request = createSampleRequest("+27821234567", "order_ready");

        WhatsAppResponse response = client.sendTemplateMessage(request);

        String messageId = response.getMessages().get(0).getId();
        assertTrue(messageId.startsWith("wamid."), "Message ID should start with 'wamid.' prefix");
        assertTrue(messageId.length() > "wamid.".length(), "Message ID should have content after prefix");
    }

    @Test
    void sendTemplateMessage_with100PercentFailureRate_throwsWhatsAppApiException() {
        MockWhatsAppClient client = new MockWhatsAppClient(100);
        WhatsAppTemplateRequest request = createSampleRequest("+27821234567", "order_confirmed");

        WhatsAppApiException exception = assertThrows(WhatsAppApiException.class,
                () -> client.sendTemplateMessage(request));

        assertEquals(500, exception.getHttpStatus());
        assertEquals("mock_error", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Mock simulated failure"));
    }

    @Test
    void sendTemplateMessage_withZeroFailureRate_neverThrows() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        WhatsAppTemplateRequest request = createSampleRequest("+27821234567", "order_confirmed");

        // Run multiple times to confirm zero failure rate means no failures
        for (int i = 0; i < 50; i++) {
            assertDoesNotThrow(() -> client.sendTemplateMessage(request));
        }
    }

    @Test
    void sendTemplateMessage_withNullTemplate_handlesGracefully() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        WhatsAppTemplateRequest request = new WhatsAppTemplateRequest("+27821234567", null);

        WhatsAppResponse response = client.sendTemplateMessage(request);

        assertNotNull(response);
        assertTrue(response.getMessages().get(0).getId().startsWith("wamid."));
    }

    @Test
    void sendTemplateMessage_withEmptyComponents_handlesGracefully() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        TemplateObject template = new TemplateObject("order_confirmed", new LanguageObject("en"), List.of());
        WhatsAppTemplateRequest request = new WhatsAppTemplateRequest("+27821234567", template);

        WhatsAppResponse response = client.sendTemplateMessage(request);

        assertNotNull(response);
        assertEquals("whatsapp", response.getMessagingProduct());
    }

    @Test
    void sendTemplateMessage_contactMatchesDestination() {
        MockWhatsAppClient client = new MockWhatsAppClient(0);
        String destination = "+44712345678";
        WhatsAppTemplateRequest request = createSampleRequest(destination, "order_receipt");

        WhatsAppResponse response = client.sendTemplateMessage(request);

        assertEquals(destination, response.getContacts().get(0).getInput());
        assertEquals(destination, response.getContacts().get(0).getWaId());
    }

    private WhatsAppTemplateRequest createSampleRequest(String to, String templateName) {
        ParameterObject param1 = new ParameterObject("text", "John");
        ParameterObject param2 = new ParameterObject("text", "A-001");

        ComponentObject component = new ComponentObject("body", List.of(param1, param2));
        LanguageObject language = new LanguageObject("en");
        TemplateObject template = new TemplateObject(templateName, language, List.of(component));

        return new WhatsAppTemplateRequest(to, template);
    }
}
