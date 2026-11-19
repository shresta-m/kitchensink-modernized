package com.kitchensink.member.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Class-level rule: the phone number must be real for the selected country code.
 * Works on any DTO; point it at the right fields if they are not called countryCode / phone.
 */
@Documented
@Constraint(validatedBy = ValidPhoneValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {
    String message() default "Enter a valid phone number for the selected country";
    String countryCode() default "countryCode";
    String phone() default "phone";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
