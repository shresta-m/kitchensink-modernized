package com.kitchensink.member.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ValidPhoneValidatorTest {

    @ValidPhone
    static class Holder {
        private final Object countryCode;
        private final Object phone;

        Holder(Object countryCode, Object phone) {
            this.countryCode = countryCode;
            this.phone = phone;
        }
    }

    private final ValidPhoneValidator validator = new ValidPhoneValidator();
    private final ConstraintValidatorContext ctx = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    @BeforeEach
    void init() {
        validator.initialize(Holder.class.getAnnotation(ValidPhone.class));
    }

    @Test
    void nullObjectIsValid() {
        assertThat(validator.isValid(null, ctx)).isTrue();
    }

    @Test
    void realNumberIsValid() {
        assertThat(validator.isValid(new Holder("+91", "8123456789"), ctx)).isTrue();
    }

    @Test
    void missingOrNonTextValuesAreLeftToFieldAnnotations() {
        assertThat(validator.isValid(new Holder(null, "8123456789"), ctx)).isTrue();
        assertThat(validator.isValid(new Holder("+91", null), ctx)).isTrue();
        assertThat(validator.isValid(new Holder(91, "8123456789"), ctx)).isTrue();
        assertThat(validator.isValid(new Holder("+91", 8123456789L), ctx)).isTrue();
        assertThat(validator.isValid(new Holder(" ", "8123456789"), ctx)).isTrue();
        assertThat(validator.isValid(new Holder("+91", " "), ctx)).isTrue();
    }

    @Test
    void invalidNumberIsReportedOnThePhoneField() {
        assertThat(validator.isValid(new Holder("+91", "9999999999"), ctx)).isFalse();
        verify(ctx).disableDefaultConstraintViolation();
    }
}
