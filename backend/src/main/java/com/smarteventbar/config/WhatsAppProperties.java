package com.smarteventbar.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for WhatsApp Cloud API integration.
 * <p>
 * When {@code enabled} is false (or unset), the WhatsApp notification channel
 * is disabled and the application starts normally without requiring any
 * WhatsApp credentials. Validation constraints on credential fields only
 * apply when {@code enabled} is true — enforced via {@link WhatsAppPropertiesValidator}.
 */
@ConfigurationProperties(prefix = "whatsapp")
@Validated
@ValidWhatsAppProperties
public class WhatsAppProperties {

    /**
     * Whether the WhatsApp notification channel is enabled.
     * When false, no WhatsApp messages are sent and credential fields are not required.
     */
    private boolean enabled = false;

    /**
     * Meta WhatsApp Phone Number ID used to send messages.
     * Required when enabled is true.
     */
    private String phoneNumberId;

    /**
     * Meta WhatsApp Cloud API access token for authentication.
     * Required when enabled is true.
     */
    private String accessToken;

    /**
     * Webhook verification token shared with Meta for endpoint verification.
     * Required when enabled is true.
     */
    private String verifyToken;

    /**
     * Meta WhatsApp Business Account ID.
     * Required when enabled is true.
     */
    private String businessAccountId;

    /**
     * Meta application secret used for webhook signature verification (HMAC-SHA256).
     * Required when enabled is true.
     */
    private String appSecret;

    /**
     * Timeout in seconds for HTTP requests to the Meta Cloud API.
     */
    @Min(1)
    private int sendTimeoutSeconds = 10;

    /**
     * Maximum outbound message send rate per second (token-bucket rate limiter).
     */
    @Min(1)
    private int maxSendRatePerSecond = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getVerifyToken() {
        return verifyToken;
    }

    public void setVerifyToken(String verifyToken) {
        this.verifyToken = verifyToken;
    }

    public String getBusinessAccountId() {
        return businessAccountId;
    }

    public void setBusinessAccountId(String businessAccountId) {
        this.businessAccountId = businessAccountId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public int getSendTimeoutSeconds() {
        return sendTimeoutSeconds;
    }

    public void setSendTimeoutSeconds(int sendTimeoutSeconds) {
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    public int getMaxSendRatePerSecond() {
        return maxSendRatePerSecond;
    }

    public void setMaxSendRatePerSecond(int maxSendRatePerSecond) {
        this.maxSendRatePerSecond = maxSendRatePerSecond;
    }
}
