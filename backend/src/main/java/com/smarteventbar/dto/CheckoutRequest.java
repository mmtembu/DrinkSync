package com.smarteventbar.dto;

/**
 * Request body for the checkout endpoint.
 * Both fields are optional — if not provided, values are pre-filled from the session.
 */
public class CheckoutRequest {

    private String customerPhone;
    private Boolean whatsappOptIn;

    public CheckoutRequest() {
    }

    public CheckoutRequest(String customerPhone, Boolean whatsappOptIn) {
        this.customerPhone = customerPhone;
        this.whatsappOptIn = whatsappOptIn;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public Boolean getWhatsappOptIn() {
        return whatsappOptIn;
    }

    public void setWhatsappOptIn(Boolean whatsappOptIn) {
        this.whatsappOptIn = whatsappOptIn;
    }
}
