package com.smarteventbar.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppPropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("When WhatsApp is disabled")
    class WhenDisabled {

        @Test
        @DisplayName("No violations when all fields are empty and enabled is false")
        void noViolationsWhenDisabledAndFieldsEmpty() {
            WhatsAppProperties props = new WhatsAppProperties();
            // enabled defaults to false

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("No violations when enabled is explicitly false with no credentials")
        void noViolationsWhenExplicitlyDisabled() {
            WhatsAppProperties props = new WhatsAppProperties();
            props.setEnabled(false);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("When WhatsApp is enabled")
    class WhenEnabled {

        @Test
        @DisplayName("No violations when all required fields are provided")
        void noViolationsWhenAllFieldsProvided() {
            WhatsAppProperties props = fullyConfigured();

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Violation when phoneNumberId is blank")
        void violationWhenPhoneNumberIdBlank() {
            WhatsAppProperties props = fullyConfigured();
            props.setPhoneNumberId("");

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("phoneNumberId");
        }

        @Test
        @DisplayName("Violation when accessToken is null")
        void violationWhenAccessTokenNull() {
            WhatsAppProperties props = fullyConfigured();
            props.setAccessToken(null);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("accessToken");
        }

        @Test
        @DisplayName("Violation when verifyToken is blank")
        void violationWhenVerifyTokenBlank() {
            WhatsAppProperties props = fullyConfigured();
            props.setVerifyToken("   ");

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("verifyToken");
        }

        @Test
        @DisplayName("Violation when businessAccountId is null")
        void violationWhenBusinessAccountIdNull() {
            WhatsAppProperties props = fullyConfigured();
            props.setBusinessAccountId(null);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("businessAccountId");
        }

        @Test
        @DisplayName("Violation when appSecret is blank")
        void violationWhenAppSecretBlank() {
            WhatsAppProperties props = fullyConfigured();
            props.setAppSecret("");

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("appSecret");
        }

        @Test
        @DisplayName("Multiple violations when all credential fields are missing")
        void multipleViolationsWhenAllMissing() {
            WhatsAppProperties props = new WhatsAppProperties();
            props.setEnabled(true);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("enabled defaults to false")
        void enabledDefaultsFalse() {
            WhatsAppProperties props = new WhatsAppProperties();
            assertThat(props.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("sendTimeoutSeconds defaults to 10")
        void sendTimeoutDefaultsTen() {
            WhatsAppProperties props = new WhatsAppProperties();
            assertThat(props.getSendTimeoutSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("maxSendRatePerSecond defaults to 50")
        void maxSendRateDefaultsFifty() {
            WhatsAppProperties props = new WhatsAppProperties();
            assertThat(props.getMaxSendRatePerSecond()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("Numeric field validation")
    class NumericValidation {

        @Test
        @DisplayName("Violation when sendTimeoutSeconds is less than 1")
        void violationWhenTimeoutLessThanOne() {
            WhatsAppProperties props = new WhatsAppProperties();
            props.setSendTimeoutSeconds(0);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("sendTimeoutSeconds");
        }

        @Test
        @DisplayName("Violation when maxSendRatePerSecond is less than 1")
        void violationWhenRateLessThanOne() {
            WhatsAppProperties props = new WhatsAppProperties();
            props.setMaxSendRatePerSecond(0);

            Set<ConstraintViolation<WhatsAppProperties>> violations = validator.validate(props);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("maxSendRatePerSecond");
        }
    }

    private WhatsAppProperties fullyConfigured() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setEnabled(true);
        props.setPhoneNumberId("123456789");
        props.setAccessToken("EAABwzLixnjYBO...");
        props.setVerifyToken("my-verify-token");
        props.setBusinessAccountId("987654321");
        props.setAppSecret("abc123secret");
        return props;
    }
}
