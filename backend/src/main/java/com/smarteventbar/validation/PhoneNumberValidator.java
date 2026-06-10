package com.smarteventbar.validation;

import org.springframework.stereotype.Component;

/**
 * Validates phone numbers against the E.164 international format.
 * <p>
 * E.164 format requires:
 * <ul>
 *   <li>Starts with "+"</li>
 *   <li>Followed by 1 to 15 digits (no spaces, dashes, or other characters)</li>
 * </ul>
 * <p>
 * Used by the OrderController to validate phone numbers at checkout.
 */
@Component
public class PhoneNumberValidator {

    private static final int MAX_DIGITS = 15;
    private static final int MIN_DIGITS = 1;

    /**
     * Validates a phone number string against E.164 format.
     *
     * @param phoneNumber the phone number to validate
     * @return a ValidationResult indicating success or failure with a descriptive error message
     */
    public ValidationResult validate(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return ValidationResult.failure("Phone number must not be null or empty");
        }

        if (!phoneNumber.startsWith("+")) {
            return ValidationResult.failure("Phone number must start with '+' followed by country code and number");
        }

        String digits = phoneNumber.substring(1);

        if (digits.isEmpty()) {
            return ValidationResult.failure("Phone number must contain digits after '+'");
        }

        if (digits.length() > MAX_DIGITS) {
            return ValidationResult.failure(
                    "Phone number must not exceed " + MAX_DIGITS + " digits after '+' (got " + digits.length() + ")");
        }

        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return ValidationResult.failure(
                        "Phone number must contain only digits after '+', but found '" + digits.charAt(i) + "' at position " + (i + 2));
            }
        }

        return ValidationResult.success();
    }
}
