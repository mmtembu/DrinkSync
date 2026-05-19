package com.smarteventbar.exception;

/**
 * Thrown by WhatsAppClient on any non-2xx response from the Meta Cloud API.
 * Contains the HTTP status code and error code from the response body.
 */
public class WhatsAppApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public WhatsAppApiException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
