package com.smarteventbar.config;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint that validates WhatsApp credential fields are not blank
 * only when the WhatsApp channel is enabled.
 */
@Documented
@Constraint(validatedBy = WhatsAppPropertiesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidWhatsAppProperties {

    String message() default "Required WhatsApp properties must not be blank when enabled";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
