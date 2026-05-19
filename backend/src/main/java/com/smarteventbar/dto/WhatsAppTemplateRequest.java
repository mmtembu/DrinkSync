package com.smarteventbar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * DTO representing a WhatsApp template message request to the Meta Cloud API.
 * Conforms to the Meta Cloud API message template schema.
 */
public class WhatsAppTemplateRequest {

    @JsonProperty("messaging_product")
    private String messagingProduct = "whatsapp";

    @JsonProperty("recipient_type")
    private String recipientType = "individual";

    private String to;

    private String type = "template";

    private TemplateObject template;

    public WhatsAppTemplateRequest() {
    }

    public WhatsAppTemplateRequest(String to, TemplateObject template) {
        this.to = to;
        this.template = template;
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public void setRecipientType(String recipientType) {
        this.recipientType = recipientType;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public TemplateObject getTemplate() {
        return template;
    }

    public void setTemplate(TemplateObject template) {
        this.template = template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WhatsAppTemplateRequest that = (WhatsAppTemplateRequest) o;
        return Objects.equals(messagingProduct, that.messagingProduct)
                && Objects.equals(recipientType, that.recipientType)
                && Objects.equals(to, that.to)
                && Objects.equals(type, that.type)
                && Objects.equals(template, that.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messagingProduct, recipientType, to, type, template);
    }

    @Override
    public String toString() {
        return "WhatsAppTemplateRequest{" +
                "messagingProduct='" + messagingProduct + '\'' +
                ", recipientType='" + recipientType + '\'' +
                ", to='" + to + '\'' +
                ", type='" + type + '\'' +
                ", template=" + template +
                '}';
    }

    /**
     * Represents the template object containing name, language, and components.
     */
    public static class TemplateObject {

        private String name;

        private LanguageObject language;

        private List<ComponentObject> components;

        public TemplateObject() {
        }

        public TemplateObject(String name, LanguageObject language, List<ComponentObject> components) {
            this.name = name;
            this.language = language;
            this.components = components;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LanguageObject getLanguage() {
            return language;
        }

        public void setLanguage(LanguageObject language) {
            this.language = language;
        }

        public List<ComponentObject> getComponents() {
            return components;
        }

        public void setComponents(List<ComponentObject> components) {
            this.components = components;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TemplateObject that = (TemplateObject) o;
            return Objects.equals(name, that.name)
                    && Objects.equals(language, that.language)
                    && Objects.equals(components, that.components);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, language, components);
        }

        @Override
        public String toString() {
            return "TemplateObject{" +
                    "name='" + name + '\'' +
                    ", language=" + language +
                    ", components=" + components +
                    '}';
        }
    }

    /**
     * Represents the language object with a language code (e.g. "en").
     */
    public static class LanguageObject {

        private String code;

        public LanguageObject() {
        }

        public LanguageObject(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LanguageObject that = (LanguageObject) o;
            return Objects.equals(code, that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code);
        }

        @Override
        public String toString() {
            return "LanguageObject{code='" + code + "'}";
        }
    }

    /**
     * Represents a component in the template (e.g. body with parameters).
     */
    public static class ComponentObject {

        private String type;

        private List<ParameterObject> parameters;

        public ComponentObject() {
        }

        public ComponentObject(String type, List<ParameterObject> parameters) {
            this.type = type;
            this.parameters = parameters;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<ParameterObject> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParameterObject> parameters) {
            this.parameters = parameters;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComponentObject that = (ComponentObject) o;
            return Objects.equals(type, that.type)
                    && Objects.equals(parameters, that.parameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, parameters);
        }

        @Override
        public String toString() {
            return "ComponentObject{" +
                    "type='" + type + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }

    /**
     * Represents a parameter within a template component (e.g. text parameter).
     */
    public static class ParameterObject {

        private String type;

        private String text;

        public ParameterObject() {
        }

        public ParameterObject(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ParameterObject that = (ParameterObject) o;
            return Objects.equals(type, that.type)
                    && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, text);
        }

        @Override
        public String toString() {
            return "ParameterObject{" +
                    "type='" + type + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }
}
