package com.kitchensink.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Class-level rule: two text fields must be equal, for example password and confirmPassword. */
@Documented
@Constraint(validatedBy = FieldsMatchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldsMatch {
    String message() default "Values do not match";
    String field();
    String confirmField();
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
