package com.kitchensink.member.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneRulesTest {

    @ParameterizedTest
    @CsvSource({"+91,8123456789", "91,'81234 56789'", "+44,7400123456"})
    void acceptsRealNumbers(String code, String phone) {
        assertThat(PhoneRules.isValid(code, phone)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"+91,123", "+91,9999999999", "+91,1234567890", "+999,123456"})
    void rejectsTooShortDummyAndUnknownCountry(String code, String phone) {
        assertThat(PhoneRules.isValid(code, phone)).isFalse();
    }

    @Test
    void rejectsMissingParts() {
        assertThat(PhoneRules.isValid(null, "8123456789")).isFalse();
        assertThat(PhoneRules.isValid("+91", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000000000", "5", "", "1234567890", "9876543210", "56789"})
    void detectsDummyNumbers(String digits) {
        assertThat(PhoneRules.isDummy(digits)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8123456789", "1123", "3211", "9825041763"})
    void acceptsNonDummyNumbers(String digits) {
        assertThat(PhoneRules.isDummy(digits)).isFalse();
    }
}
