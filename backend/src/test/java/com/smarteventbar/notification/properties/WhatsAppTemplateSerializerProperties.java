package com.smarteventbar.notification.properties;

import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ComponentObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.LanguageObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.ParameterObject;
import com.smarteventbar.dto.WhatsAppTemplateRequest.TemplateObject;
import com.smarteventbar.notification.WhatsAppTemplateSerializer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Property-based tests for WhatsAppTemplateSerializer.
 *
 * Property 1: Template message serialization round-trip
 * For any valid WhatsAppTemplateRequest object (with any combination of template name,
 * language code, recipient phone number, and parameter values), serializing to JSON and
 * then deserializing back SHALL produce an object equal to the original.
 *
 * Validates: Requirements 13.5, 13.1, 13.2, 13.3, 13.4
 */
class WhatsAppTemplateSerializerProperties {

    private final WhatsAppTemplateSerializer serializer = new WhatsAppTemplateSerializer();

    @Provide
    Arbitrary<String> templateNames() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(30);
    }

    @Provide
    Arbitrary<String> languageCodes() {
        return Arbitraries.of("en", "fr", "de", "es", "pt", "it", "nl", "ja", "zh", "ar");
    }

    @Provide
    Arbitrary<String> e164PhoneNumbers() {
        // E.164: + followed by 1-15 digits, first digit non-zero
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(13).map(rest ->
                        "+" + firstDigit + rest
                )
        );
    }

    @Provide
    Arbitrary<String> parameterTexts() {
        return Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50)
                .filter(s -> !s.contains("\u0000")); // exclude null chars
    }

    @Provide
    Arbitrary<WhatsAppTemplateRequest> templateRequests() {
        return Combinators.combine(
                e164PhoneNumbers(),
                templateNames(),
                languageCodes(),
                Arbitraries.integers().between(1, 5)
        ).flatAs((phone, name, langCode, paramCount) -> {
            Arbitrary<List<ParameterObject>> paramsArb = parameterTexts()
                    .list().ofSize(paramCount)
                    .map(texts -> texts.stream()
                            .map(text -> new ParameterObject("text", text))
                            .collect(Collectors.toList()));

            return paramsArb.map(params -> {
                LanguageObject language = new LanguageObject(langCode);
                ComponentObject component = new ComponentObject("body", params);
                TemplateObject template = new TemplateObject(name, language, List.of(component));

                WhatsAppTemplateRequest request = new WhatsAppTemplateRequest(phone, template);
                request.setMessagingProduct("whatsapp");
                request.setRecipientType("individual");
                request.setType("template");
                return request;
            });
        });
    }

    /**
     * Property 1: Serialization round-trip produces equal object.
     * For any valid WhatsAppTemplateRequest, serialize → deserialize == original.
     */
    @Property
    void serializeDeserializeRoundTripProducesEqualObject(
            @ForAll("templateRequests") WhatsAppTemplateRequest original) {
        String json = serializer.serialize(original);
        WhatsAppTemplateRequest deserialized = serializer.deserialize(json);

        assert original.equals(deserialized) :
                "Round-trip failed.\nOriginal: " + original + "\nDeserialized: " + deserialized + "\nJSON: " + json;
    }

    /**
     * Property 1b: Serialization produces valid JSON (non-null, non-empty).
     */
    @Property
    void serializationProducesNonEmptyJson(
            @ForAll("templateRequests") WhatsAppTemplateRequest request) {
        String json = serializer.serialize(request);

        assert json != null : "Serialized JSON should not be null";
        assert !json.isBlank() : "Serialized JSON should not be blank";
        assert json.startsWith("{") : "Serialized JSON should start with '{'";
        assert json.endsWith("}") : "Serialized JSON should end with '}'";
    }

    /**
     * Property 1c: Deserialized object preserves all key fields.
     */
    @Property
    void deserializedObjectPreservesAllFields(
            @ForAll("templateRequests") WhatsAppTemplateRequest original) {
        String json = serializer.serialize(original);
        WhatsAppTemplateRequest deserialized = serializer.deserialize(json);

        assert original.getMessagingProduct().equals(deserialized.getMessagingProduct()) :
                "messagingProduct mismatch";
        assert original.getRecipientType().equals(deserialized.getRecipientType()) :
                "recipientType mismatch";
        assert original.getTo().equals(deserialized.getTo()) :
                "to (phone number) mismatch";
        assert original.getType().equals(deserialized.getType()) :
                "type mismatch";
        assert original.getTemplate().getName().equals(deserialized.getTemplate().getName()) :
                "template name mismatch";
        assert original.getTemplate().getLanguage().getCode()
                .equals(deserialized.getTemplate().getLanguage().getCode()) :
                "language code mismatch";
    }
}
