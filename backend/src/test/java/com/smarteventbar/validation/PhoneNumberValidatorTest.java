package com.smarteventbar.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberValidatorTest {

    private PhoneNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PhoneNumberValidator();
    }

    @Test
    void validE164PhoneNumber_returnsSuccess() {
        ValidationResult result = validator.validate("+27821234567");
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void validMinimalPhoneNumber_returnsSuccess() {
        // Minimum: + followed by 1 digit
        ValidationResult result = validator.validate("+1");
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void validMaxLengthPhoneNumber_returnsSuccess() {
        // Maximum: + followed by 15 digits
        ValidationResult result = validator.validate("+123456789012345");
        assertTrue(result.valid());
        assertNull(result.errorMessage());
    }

    @Test
    void nullPhoneNumber_returnsFailure() {
        ValidationResult result = validator.validate(null);
        assertFalse(result.valid());
        assertEquals("Phone number must not be null or empty", result.errorMessage());
    }

    @Test
    void emptyPhoneNumber_returnsFailure() {
        ValidationResult result = validator.validate("");
        assertFalse(result.valid());
        assertEquals("Phone number must not be null or empty", result.errorMessage());
    }

    @Test
    void blankPhoneNumber_returnsFailure() {
        ValidationResult result = validator.validate("   ");
        assertFalse(result.valid());
        assertEquals("Phone number must not be null or empty", result.errorMessage());
    }

    @Test
    void missingPlusPrefix_returnsFailure() {
        ValidationResult result = validator.validate("27821234567");
        assertFalse(result.valid());
        assertEquals("Phone number must start with '+' followed by country code and number", result.errorMessage());
    }

    @Test
    void onlyPlusSign_returnsFailure() {
        ValidationResult result = validator.validate("+");
        assertFalse(result.valid());
        assertEquals("Phone number must contain digits after '+'", result.errorMessage());
    }

    @Test
    void tooManyDigits_returnsFailure() {
        // 16 digits after +
        ValidationResult result = validator.validate("+1234567890123456");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("must not exceed 15 digits"));
    }

    @Test
    void containsLetters_returnsFailure() {
        ValidationResult result = validator.validate("+27abc1234567");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("must contain only digits after '+'"));
        assertTrue(result.errorMessage().contains("'a'"));
    }

    @Test
    void containsSpaces_returnsFailure() {
        ValidationResult result = validator.validate("+27 82 123 4567");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("must contain only digits after '+'"));
    }

    @Test
    void containsDashes_returnsFailure() {
        ValidationResult result = validator.validate("+27-82-123-4567");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("must contain only digits after '+'"));
    }

    @Test
    void containsParentheses_returnsFailure() {
        ValidationResult result = validator.validate("+(27)821234567");
        assertFalse(result.valid());
        assertTrue(result.errorMessage().contains("must contain only digits after '+'"));
    }
}
