package com.kitchensink.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.DirectFieldAccessor;

public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {

    private String field;
    private String confirmField;

    @Override
    public void initialize(FieldsMatch annotation) {
        this.field = annotation.field();
        this.confirmField = annotation.confirmField();
    }

    @Override
    public boolean isValid(Object dto, ConstraintValidatorContext ctx) {
        if (dto == null) {
            return true;
        }
        DirectFieldAccessor fields = new DirectFieldAccessor(dto);
        Object first = fields.getPropertyValue(field);
        Object second = fields.getPropertyValue(confirmField);

        if (first == null || second == null || first.equals(second)) {   // missing values: field-level annotations report
            return true;
        }
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
           .addPropertyNode(confirmField).addConstraintViolation();
        return false;
    }
}
