package com.smarteventbar.validation;

/**
 * Represents the result of a validation operation.
 *
 * @param valid whether the validated value is valid
 * @param errorMessage descriptive error message when invalid, null when valid
 */
public record ValidationResult(boolean valid, String errorMessage) {

    /**
     * Creates a successful validation result.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    /**
     * Creates a failed validation result with a descriptive error message.
     */
    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
}
