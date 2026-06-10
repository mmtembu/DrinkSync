package com.smarteventbar.notification;

import com.smarteventbar.config.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies HMAC-SHA256 signatures on inbound webhook payloads from Meta.
 * Uses the configured App Secret as the HMAC key.
 */
@Component
public class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsAppProperties whatsAppProperties;

    public SignatureVerifier(WhatsAppProperties whatsAppProperties) {
        this.whatsAppProperties = whatsAppProperties;
    }

    /**
     * Verify HMAC-SHA256 signature of webhook payload.
     *
     * @param rawBody         the raw request body as a string
     * @param signatureHeader the value of the X-Hub-Signature-256 header (e.g. "sha256=abc123...")
     * @return true if the signature matches, false otherwise
     */
    public boolean verify(String rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null) {
            return false;
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook signature header does not start with expected prefix 'sha256='");
            return false;
        }

        String providedHash = signatureHeader.substring(SIGNATURE_PREFIX.length());
        String computedHash = computeHmacSha256(rawBody, whatsAppProperties.getAppSecret());

        if (computedHash == null) {
            return false;
        }

        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                providedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Compute HMAC-SHA256 hash of the given data using the provided secret.
     *
     * @param data   the data to hash
     * @param secret the secret key
     * @return hex-encoded hash string, or null on error
     */
    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC-SHA256: {}", e.getMessage(), e);
            return null;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
