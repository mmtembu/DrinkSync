package com.smarteventbar.notification;

import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ComponentObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.LanguageObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ParameterObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.TemplateObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppTemplateSerializerTest {

    private WhatsAppTemplateSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new WhatsAppTemplateSerializer();
    }

    @Test
    void serialize_validRequest_producesValidJson() {
        WhatsAppTemplateRequest request = createSampleRequest();

        String json = serializer.serialize(request);

        assertNotNull(json);
        assertTrue(json.contains("\"messaging_product\":\"whatsapp\""));
        assertTrue(json.contains("\"recipient_type\":\"individual\""));
        assertTrue(json.contains("\"to\":\"+27821234567\""));
        assertTrue(json.contains("\"type\":\"template\""));
        assertTrue(json.contains("\"name\":\"order_confirmed\""));
        assertTrue(json.contains("\"code\":\"en\""));
    }

    @Test
    void deserialize_validJson_producesCorrectObject() {
        String json = """
                {
                  "messaging_product": "whatsapp",
                  "recipient_type": "individual",
                  "to": "+27821234567",
                  "type": "template",
                  "template": {
                    "name": "order_confirmed",
                    "language": { "code": "en" },
                    "components": [
                      {
                        "type": "body",
                        "parameters": [
                          { "type": "text", "text": "John" },
                          { "type": "text", "text": "A-001" }
                        ]
                      }
                    ]
                  }
                }
                """;

        WhatsAppTemplateRequest result = serializer.deserialize(json);

        assertEquals("whatsapp", result.getMessagingProduct());
        assertEquals("individual", result.getRecipientType());
        assertEquals("+27821234567", result.getTo());
        assertEquals("template", result.getType());
        assertEquals("order_confirmed", result.getTemplate().getName());
        assertEquals("en", result.getTemplate().getLanguage().getCode());
        assertEquals(1, result.getTemplate().getComponents().size());
        assertEquals(2, result.getTemplate().getComponents().get(0).getParameters().size());
        assertEquals("John", result.getTemplate().getComponents().get(0).getParameters().get(0).getText());
    }

    @Test
    void roundTrip_serializeAndDeserialize_producesEqualObject() {
        WhatsAppTemplateRequest original = createSampleRequest();

        String json = serializer.serialize(original);
        WhatsAppTemplateRequest deserialized = serializer.deserialize(json);

        assertEquals(original, deserialized);
    }

    @Test
    void deserialize_unknownProperties_doesNotFail() {
        String json = """
                {
                  "messaging_product": "whatsapp",
                  "recipient_type": "individual",
                  "to": "+27821234567",
                  "type": "template",
                  "unknown_field": "should be ignored",
                  "template": {
                    "name": "order_confirmed",
                    "language": { "code": "en" },
                    "components": []
                  }
                }
                """;

        WhatsAppTemplateRequest result = assertDoesNotThrow(() -> serializer.deserialize(json));
        assertEquals("whatsapp", result.getMessagingProduct());
        assertEquals("+27821234567", result.getTo());
    }

    @Test
    void serialize_nullRequest_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> serializer.serialize(null));
    }

    @Test
    void deserialize_invalidJson_throwsRuntimeException() {
        String invalidJson = "{ this is not valid json }";

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> serializer.deserialize(invalidJson));
        assertTrue(exception.getMessage().contains("Failed to deserialize JSON to WhatsAppTemplateRequest"));
    }

    @Test
    void deserialize_emptyString_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> serializer.deserialize(""));
    }

    private WhatsAppTemplateRequest createSampleRequest() {
        ParameterObject param1 = new ParameterObject("text", "John");
        ParameterObject param2 = new ParameterObject("text", "A-001");
        ParameterObject param3 = new ParameterObject("text", "Main Bar");

        ComponentObject component = new ComponentObject("body", List.of(param1, param2, param3));
        LanguageObject language = new LanguageObject("en");
        TemplateObject template = new TemplateObject("order_confirmed", language, List.of(component));

        return new WhatsAppTemplateRequest("+27821234567", template);
    }
}
