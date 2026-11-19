package com.kitchensink.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FieldsMatchValidatorTest {

    @FieldsMatch(field = "first", confirmField = "second", message = "must match")
    static class Pair {
        private final String first;
        private final String second;

        Pair(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    private final FieldsMatchValidator validator = new FieldsMatchValidator();
    private final ConstraintValidatorContext ctx = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    @BeforeEach
    void init() {
        validator.initialize(Pair.class.getAnnotation(FieldsMatch.class));
    }

    @Test
    void nullObjectIsValid() {
        assertThat(validator.isValid(null, ctx)).isTrue();
    }

    @Test
    void equalValuesAreValid() {
        assertThat(validator.isValid(new Pair("a", "a"), ctx)).isTrue();
    }

    @Test
    void missingValuesAreLeftToFieldAnnotations() {
        assertThat(validator.isValid(new Pair(null, "a"), ctx)).isTrue();
        assertThat(validator.isValid(new Pair("a", null), ctx)).isTrue();
    }

    @Test
    void differentValuesAreReportedOnTheConfirmField() {
        assertThat(validator.isValid(new Pair("a", "b"), ctx)).isFalse();
        verify(ctx).disableDefaultConstraintViolation();
    }
}
