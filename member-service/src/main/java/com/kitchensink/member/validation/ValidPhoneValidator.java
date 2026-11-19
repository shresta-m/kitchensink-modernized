package com.kitchensink.member.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.DirectFieldAccessor;

public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, Object> {

    private String countryCodeField;
    private String phoneField;

    @Override
    public void initialize(ValidPhone annotation) {
        this.countryCodeField = annotation.countryCode();
        this.phoneField = annotation.phone();
    }

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext ctx) {
        if (dto == null) {
            return true;
        }
        DirectFieldAccessor fields = new DirectFieldAccessor(dto);
        Object code = fields.getPropertyValue(countryCodeField);
        Object phone = fields.getPropertyValue(phoneField);

        // Missing values are reported by the field-level annotations, so skip here to avoid duplicate errors.
        if (!(code instanceof String c) || !(phone instanceof String p) || c.isBlank() || p.isBlank()) {
            return true;
        }
        if (PhoneRules.isValid(c, p)) {
            return true;
        }
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
           .addPropertyNode(phoneField).addConstraintViolation();   // shows under the phone input
        return false;
    }
}
