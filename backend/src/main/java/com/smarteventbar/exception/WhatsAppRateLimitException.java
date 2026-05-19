package com.smarteventbar.exception;

import java.time.Duration;

/**
 * Thrown by WhatsAppClient specifically on HTTP 429 (Too Many Requests) responses
 * from the Meta Cloud API. Includes the duration to wait before retrying.
 */
public class WhatsAppRateLimitException extends WhatsAppApiException {

    private final Duration retryAfter;

    public WhatsAppRateLimitException(String message, Duration retryAfter) {
        super(message, 429, "rate_limited");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
