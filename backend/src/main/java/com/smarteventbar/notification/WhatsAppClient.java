package com.smarteventbar.notification;

import com.smarteventbar.dto.WhatsAppResponse;
import com.smarteventbar.dto.WhatsAppTemplateRequest;
import com.smarteventbar.exception.WhatsAppApiException;
import com.smarteventbar.exception.WhatsAppRateLimitException;

/**
 * Client interface for communicating with the Meta WhatsApp Cloud API.
 * Implementations handle HTTP communication, authentication, and error mapping.
 *
 * <p>Sends HTTP POST requests to the Meta Cloud API messages endpoint at
 * {@code https://graph.facebook.com/v21.0/{phone_number_id}/messages} with
 * Content-Type set to {@code application/json}.</p>
 */
public interface WhatsAppClient {

    /**
     * Send a template message via the Meta WhatsApp Cloud API.
     *
     * @param request the template message request containing recipient, template name,
     *                language, and parameter values
     * @return the API response containing the provider message ID on success
     * @throws WhatsAppApiException if the Meta Cloud API returns an HTTP error status
     *         (4xx or 5xx), containing the error code and error message from the response body
     * @throws WhatsAppRateLimitException if the Meta Cloud API returns HTTP 429 (Too Many Requests),
     *         containing the Retry-After duration from the response headers
     */
    WhatsAppResponse sendTemplateMessage(WhatsAppTemplateRequest request);
}
