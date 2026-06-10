package com.smarteventbar.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * DTO representing the response from the Meta WhatsApp Cloud API
 * after sending a template message.
 */
public class WhatsAppResponse {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    private List<Contact> contacts;

    private List<Message> messages;

    public WhatsAppResponse() {
    }

    public WhatsAppResponse(String messagingProduct, List<Contact> contacts, List<Message> messages) {
        this.messagingProduct = messagingProduct;
        this.contacts = contacts;
        this.messages = messages;
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }

    public void setMessagingProduct(String messagingProduct) {
        this.messagingProduct = messagingProduct;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public void setContacts(List<Contact> contacts) {
        this.contacts = contacts;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WhatsAppResponse that = (WhatsAppResponse) o;
        return Objects.equals(messagingProduct, that.messagingProduct)
                && Objects.equals(contacts, that.contacts)
                && Objects.equals(messages, that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messagingProduct, contacts, messages);
    }

    @Override
    public String toString() {
        return "WhatsAppResponse{" +
                "messagingProduct='" + messagingProduct + '\'' +
                ", contacts=" + contacts +
                ", messages=" + messages +
                '}';
    }

    /**
     * Represents a contact in the WhatsApp API response.
     */
    public static class Contact {

        private String input;

        @JsonProperty("wa_id")
        private String waId;

        public Contact() {
        }

        public Contact(String input, String waId) {
            this.input = input;
            this.waId = waId;
        }

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getWaId() {
            return waId;
        }

        public void setWaId(String waId) {
            this.waId = waId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Contact contact = (Contact) o;
            return Objects.equals(input, contact.input)
                    && Objects.equals(waId, contact.waId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(input, waId);
        }

        @Override
        public String toString() {
            return "Contact{" +
                    "input='" + input + '\'' +
                    ", waId='" + waId + '\'' +
                    '}';
        }
    }

    /**
     * Represents a message in the WhatsApp API response.
     */
    public static class Message {

        private String id;

        public Message() {
        }

        public Message(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Message message = (Message) o;
            return Objects.equals(id, message.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Message{id='" + id + "'}";
        }
    }
}
