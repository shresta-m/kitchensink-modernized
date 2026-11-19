package com.kitchensink.member.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.*;

@NotBlank
@Size(max = MemberRules.EMAIL_MAX)
@Pattern(regexp = MemberRules.EMAIL)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {
    String message() default MemberRules.EMAIL_MESSAGE;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
