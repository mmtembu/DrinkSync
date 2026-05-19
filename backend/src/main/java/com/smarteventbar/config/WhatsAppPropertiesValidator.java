package com.smarteventbar.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that required WhatsApp credential fields are not blank when the
 * WhatsApp channel is enabled ({@code whatsapp.enabled=true}).
 * <p>
 * When disabled, all credential fields are optional, allowing the application
 * to start without WhatsApp configuration (graceful degradation).
 */
public class WhatsAppPropertiesValidator
        implements ConstraintValidator<ValidWhatsAppProperties, WhatsAppProperties> {

    @Override
    public boolean isValid(WhatsAppProperties properties, ConstraintValidatorContext context) {
        if (properties == null || !properties.isEnabled()) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (isBlank(properties.getPhoneNumberId())) {
            context.buildConstraintViolationWithTemplate(
                    "whatsapp.phone-number-id must not be blank when WhatsApp is enabled")
                    .addPropertyNode("phoneNumberId")
                    .addConstraintViolation();
            valid = false;
        }

        if (isBlank(properties.getAccessToken())) {
            context.buildConstraintViolationWithTemplate(
                    "whatsapp.access-token must not be blank when WhatsApp is enabled")
                    .addPropertyNode("accessToken")
                    .addConstraintViolation();
            valid = false;
        }

        if (isBlank(properties.getVerifyToken())) {
            context.buildConstraintViolationWithTemplate(
                    "whatsapp.verify-token must not be blank when WhatsApp is enabled")
                    .addPropertyNode("verifyToken")
                    .addConstraintViolation();
            valid = false;
        }

        if (isBlank(properties.getBusinessAccountId())) {
            context.buildConstraintViolationWithTemplate(
                    "whatsapp.business-account-id must not be blank when WhatsApp is enabled")
                    .addPropertyNode("businessAccountId")
                    .addConstraintViolation();
            valid = false;
        }

        if (isBlank(properties.getAppSecret())) {
            context.buildConstraintViolationWithTemplate(
                    "whatsapp.app-secret must not be blank when WhatsApp is enabled")
                    .addPropertyNode("appSecret")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
