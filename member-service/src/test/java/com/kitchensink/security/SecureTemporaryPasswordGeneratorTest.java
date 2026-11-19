package com.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.member.validation.MemberRules;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureTemporaryPasswordGeneratorTest {

    private final SecureTemporaryPasswordGenerator generator = new SecureTemporaryPasswordGenerator();

    @Test
    void generatesTwelveCharactersThatPassThePasswordRuleWithNoLookAlikes() {
        for (int i = 0; i < 2_000; i++) {
            String password = generator.generate();
            assertThat(password).hasSize(12)
                    .matches(MemberRules.PASSWORD_FORMAT)
                    .doesNotContainPattern("[0OlI1]");
        }
    }

    @Test
    void generatesDifferentPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(1_000);
    }
}
