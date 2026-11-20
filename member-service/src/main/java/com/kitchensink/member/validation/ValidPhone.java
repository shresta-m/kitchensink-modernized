package com.kitchensink.member.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

import static com.kitchensink.member.constants.MemberConstants.*;

/**
 * Class-level rule: the phone number must be real for the selected country code.
 * Works on any DTO; point it at the right fields if they are not called countryCode / phone.
 */
@Documented
@Constraint(validatedBy = ValidPhoneValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default VALID_PHONE_NUMBER_FOR_THE_SELECTED_COUNTRY;
    String countryCode() default COUNTRY_CODE;
    String phone() default PHONE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
