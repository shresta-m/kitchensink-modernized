package com.kitchensink.member.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberRulesTest {

    @Test
    void normalizesName() {
        assertThat(MemberRules.normalizeName("  Asha   Rao ")).isEqualTo("Asha Rao");
    }

    @Test
    void normalizesEmail() {
        assertThat(MemberRules.normalizeEmail("  Asha@Example.COM ")).isEqualTo("asha@example.com");
    }

    @Test
    void normalizesCountryCodeWithAndWithoutPlus() {
        assertThat(MemberRules.normalizeCountryCode("91")).isEqualTo("+91");
        assertThat(MemberRules.normalizeCountryCode(" +91 ")).isEqualTo("+91");
    }

    @Test
    void normalizesPhoneToDigits() {
        assertThat(MemberRules.normalizePhone("98765 43210")).isEqualTo("9876543210");
        assertThat(MemberRules.normalizePhone("98765-43210")).isEqualTo("9876543210");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void rejectsMissingValues(String value) {
        for (UnaryOperator<String> normalizer : java.util.List.<UnaryOperator<String>>of(
                MemberRules::normalizeName, MemberRules::normalizeEmail,
                MemberRules::normalizeCountryCode, MemberRules::normalizePhone)) {
            assertThatThrownBy(() -> normalizer.apply(value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be blank");
        }
    }
}
