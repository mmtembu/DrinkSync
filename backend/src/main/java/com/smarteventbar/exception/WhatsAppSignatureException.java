package com.smarteventbar.exception;

/**
 * Thrown when webhook signature verification fails.
 * Indicates the inbound webhook payload could not be verified
 * against the expected HMAC-SHA256 signature.
 */
public class WhatsAppSignatureException extends RuntimeException {

    public WhatsAppSignatureException(String message) {
        super(message);
    }
}
