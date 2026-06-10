package com.smarteventbar.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import org.springframework.stereotype.Component;

/**
 * Serializer for WhatsApp template message requests.
 * Handles conversion between WhatsAppTemplateRequest objects and JSON strings
 * for communication with the Meta Cloud API.
 */
@Component
public class WhatsAppTemplateSerializer {

    private final ObjectMapper objectMapper;

    public WhatsAppTemplateSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Serialize a template message request to JSON for Meta Cloud API.
     *
     * @param request the WhatsApp template request to serialize
     * @return JSON string representation of the request
     * @throws RuntimeException if serialization fails or request is null
     */
    public String serialize(WhatsAppTemplateRequest request) {
        if (request == null) {
            throw new RuntimeException("Cannot serialize null WhatsAppTemplateRequest");
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize WhatsAppTemplateRequest to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserialize a JSON string back to a template message request.
     *
     * @param json the JSON string to deserialize
     * @return the deserialized WhatsAppTemplateRequest object
     * @throws RuntimeException if deserialization fails
     */
    public WhatsAppTemplateRequest deserialize(String json) {
        try {
            return objectMapper.readValue(json, WhatsAppTemplateRequest.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to deserialize JSON to WhatsAppTemplateRequest: " + e.getMessage(), e);
        }
    }
}
