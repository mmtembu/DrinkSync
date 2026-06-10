package com.smarteventbar.notification.properties;

import com.smarteventbar.validation.PhoneNumberValidator;
import com.smarteventbar.validation.ValidationResult;
import net.jqwik.api.*;

/**
 * Property-based tests for PhoneNumberValidator.
 *
 * Property 2: E.164 phone number validation
 * For any string, the phone number validator SHALL accept it if and only if it matches
 * the E.164 format (starts with "+", followed by 1–15 digits with a valid country code).
 * All other strings SHALL be rejected.
 *
 * Validates: Requirements 1.5, 1.6
 */
class PhoneNumberValidatorProperties {

    private final PhoneNumberValidator validator = new PhoneNumberValidator();

    @Provide
    Arbitrary<String> validE164PhoneNumbers() {
        // E.164: + followed by 1-15 digits, first digit non-zero
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(14).map(rest ->
                        "+" + firstDigit + rest
                )
        );
    }

    @Provide
    Arbitrary<String> missingPlusPrefix() {
        // Digits without the leading +
        return Arbitraries.integers().between(1, 9).flatMap(firstDigit ->
                Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(14).map(rest ->
                        String.valueOf(firstDigit) + rest
                )
        );
    }

    @Provide
    Arbitrary<String> tooManyDigits() {
        // + followed by more than 15 digits
        return Arbitraries.strings().numeric().ofMinLength(16).ofMaxLength(25).map(digits ->
                "+" + digits
        );
    }

    @Provide
    Arbitrary<String> containsLetters() {
        // + followed by a mix of digits and at least one letter
        return Arbitraries.strings().numeric().ofMinLength(1).ofMaxLength(10).flatMap(prefix ->
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(3).flatMap(letters ->
                        Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(5).map(suffix ->
                                "+" + prefix + letters + suffix
                        )
                )
        );
    }

    @Provide
    Arbitrary<String> emptyOrBlank() {
        return Arbitraries.of("", " ", "  ", "\t", "\n");
    }

    /**
     * Property 2a: Valid E.164 phone numbers are accepted.
     */
    @Property
    void validE164NumbersAreAccepted(@ForAll("validE164PhoneNumbers") String phone) {
        ValidationResult result = validator.validate(phone);

        assert result.valid() :
                "Expected valid E.164 number to be accepted: '" + phone + "', error: " + result.errorMessage();
    }

    /**
     * Property 2b: Numbers missing the + prefix are rejected.
     */
    @Property
    void numbersMissingPlusPrefixAreRejected(@ForAll("missingPlusPrefix") String phone) {
        ValidationResult result = validator.validate(phone);

        assert !result.valid() :
                "Expected number without '+' prefix to be rejected: '" + phone + "'";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for invalid phone: '" + phone + "'";
    }

    /**
     * Property 2c: Numbers with too many digits are rejected.
     */
    @Property
    void numbersWithTooManyDigitsAreRejected(@ForAll("tooManyDigits") String phone) {
        ValidationResult result = validator.validate(phone);

        assert !result.valid() :
                "Expected number with >15 digits to be rejected: '" + phone + "'";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for too-long phone: '" + phone + "'";
    }

    /**
     * Property 2d: Numbers containing letters are rejected.
     */
    @Property
    void numbersContainingLettersAreRejected(@ForAll("containsLetters") String phone) {
        ValidationResult result = validator.validate(phone);

        assert !result.valid() :
                "Expected number with letters to be rejected: '" + phone + "'";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for phone with letters: '" + phone + "'";
    }

    /**
     * Property 2e: Empty or blank strings are rejected.
     */
    @Property
    void emptyOrBlankStringsAreRejected(@ForAll("emptyOrBlank") String phone) {
        ValidationResult result = validator.validate(phone);

        assert !result.valid() :
                "Expected empty/blank string to be rejected: '" + phone + "'";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for empty/blank phone";
    }

    /**
     * Property 2f: Null input is rejected.
     */
    @Property
    void nullInputIsRejected() {
        ValidationResult result = validator.validate(null);

        assert !result.valid() : "Expected null to be rejected";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for null phone";
    }

    /**
     * Property 2g: Just "+" with no digits is rejected.
     */
    @Property
    void justPlusSignIsRejected() {
        ValidationResult result = validator.validate("+");

        assert !result.valid() : "Expected '+' alone to be rejected";
        assert result.errorMessage() != null && !result.errorMessage().isBlank() :
                "Expected descriptive error message for '+' alone";
    }
}
