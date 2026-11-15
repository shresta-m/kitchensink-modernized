package com.kitchensink.validation;




import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class RulesTest {

    @Test
    void usernameIsTrimmedAndLowercased() {
        assertThat(UserRules.normalizeUsername("  Jane.Doe ")).isEqualTo("jane.doe");
        assertThat(UserRules.normalizeUsername(null)).isEmpty();
        assertThat("Ab_c-1.x").matches(UserRules.USERNAME_REGEX);
        assertThat("ab").doesNotMatch(UserRules.USERNAME_REGEX);
        assertThat("has space").doesNotMatch(UserRules.USERNAME_REGEX);
    }

    @Test
    void memberFieldsAreNormalized() {
        assertThat(MemberRules.normalizeName("  Ann ")).isEqualTo("Ann");
        assertThat(MemberRules.normalizeEmail("  A@B.Co ")).isEqualTo("a@b.co");
        assertThat(MemberRules.normalizePhone(" 2125551212 ")).isEqualTo("2125551212");
    }

    @Test
    void utilityClassesCannotBeInstantiated() throws Exception {
        for (Class<?> c : new Class<?>[] {UserRules.class, MemberRules.class}) {
            Constructor<?> ctor = c.getDeclaredConstructor();
            assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
            ctor.setAccessible(true);
            assertThat(ctor.newInstance()).isNotNull();
        }
    }
}
