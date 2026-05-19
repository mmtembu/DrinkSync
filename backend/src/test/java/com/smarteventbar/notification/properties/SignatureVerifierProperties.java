package com.smarteventbar.notification.properties;

import com.smarteventbar.config.WhatsAppProperties;
import com.smarteventbar.notification.SignatureVerifier;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Property-based tests for SignatureVerifier.
 *
 * Property 3: Webhook signature verification correctness
 * For any raw request body and secret key, the signature verifier SHALL return true
 * when given the correct HMAC-SHA256 hash (prefixed with "sha256=") and SHALL return
 * false for any other signature value.
 *
 * Validates: Requirements 14.2, 14.3, 14.4
 */
class SignatureVerifierProperties {

    @Provide
    Arbitrary<String> requestBodies() {
        return Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(1000)
                .filter(s -> !s.contains("\u0000"));
    }

    @Provide
    Arbitrary<String> secrets() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> incorrectSignatures() {
        return Arbitraries.oneOf(
                // Random hex strings (wrong hash)
                Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f')
                        .ofMinLength(64).ofMaxLength(64)
                        .map(hex -> "sha256=" + hex),
                // Missing prefix
                Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f')
                        .ofMinLength(64).ofMaxLength(64),
                // Wrong prefix
                Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f')
                        .ofMinLength(64).ofMaxLength(64)
                        .map(hex -> "sha1=" + hex),
                // Too short hash
                Arbitraries.strings().withCharRange('0', '9').withCharRange('a', 'f')
                        .ofMinLength(10).ofMaxLength(30)
                        .map(hex -> "sha256=" + hex)
        );
    }

    private SignatureVerifier createVerifier(String secret) {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setAppSecret(secret);
        props.setEnabled(true);
        return new SignatureVerifier(props);
    }

    private String computeCorrectSignature(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256", e);
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

    /**
     * Property 3a: Correct HMAC-SHA256 signature is accepted.
     * For any body and secret, computing the correct HMAC and prefixing with "sha256="
     * SHALL cause verify() to return true.
     */
    @Property
    void correctSignatureIsAccepted(
            @ForAll("requestBodies") String body,
            @ForAll("secrets") String secret) {
        SignatureVerifier verifier = createVerifier(secret);
        String correctSignature = computeCorrectSignature(body, secret);

        boolean result = verifier.verify(body, correctSignature);

        assert result :
                "Expected correct signature to be accepted for body='" + body.substring(0, Math.min(50, body.length()))
                        + "...' with secret='" + secret + "'";
    }

    /**
     * Property 3b: Incorrect signature is rejected.
     * For any body and secret, providing any signature other than the correct HMAC
     * SHALL cause verify() to return false.
     */
    @Property
    void incorrectSignatureIsRejected(
            @ForAll("requestBodies") String body,
            @ForAll("secrets") String secret,
            @ForAll("incorrectSignatures") String wrongSignature) {
        SignatureVerifier verifier = createVerifier(secret);
        String correctSignature = computeCorrectSignature(body, secret);

        // Skip if the random wrong signature happens to match the correct one (astronomically unlikely)
        Assume.that(!wrongSignature.equals(correctSignature));

        boolean result = verifier.verify(body, wrongSignature);

        assert !result :
                "Expected incorrect signature to be rejected. Wrong: '" + wrongSignature + "'";
    }

    /**
     * Property 3c: Null body returns false.
     */
    @Property
    void nullBodyReturnsFalse(@ForAll("secrets") String secret) {
        SignatureVerifier verifier = createVerifier(secret);

        boolean result = verifier.verify(null, "sha256=abc123");

        assert !result : "Expected null body to return false";
    }

    /**
     * Property 3d: Null signature header returns false.
     */
    @Property
    void nullSignatureReturnsFalse(
            @ForAll("requestBodies") String body,
            @ForAll("secrets") String secret) {
        SignatureVerifier verifier = createVerifier(secret);

        boolean result = verifier.verify(body, null);

        assert !result : "Expected null signature to return false";
    }

    /**
     * Property 3e: Signature computed with a different secret is rejected.
     */
    @Property
    void signatureWithDifferentSecretIsRejected(
            @ForAll("requestBodies") String body,
            @ForAll("secrets") String secret1,
            @ForAll("secrets") String secret2) {
        Assume.that(!secret1.equals(secret2));

        SignatureVerifier verifier = createVerifier(secret1);
        // Compute signature with a different secret
        String wrongSignature = computeCorrectSignature(body, secret2);

        boolean result = verifier.verify(body, wrongSignature);

        assert !result :
                "Expected signature computed with different secret to be rejected";
    }
}
