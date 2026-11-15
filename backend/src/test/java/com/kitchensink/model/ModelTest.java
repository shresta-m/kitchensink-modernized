package com.kitchensink.model;




import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ModelTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void applyReplacesEditableFieldsButKeepsCreationTime() {
        Member m = Member.register("Ann", "ann@example.com", "2125551212", T1);

        m.apply("  Bob Ray ", "BOB@Example.com", "2125559999", T2);

        assertThat(m.getName()).isEqualTo("Bob Ray");
        assertThat(m.getNameLower()).isEqualTo("bob ray");
        assertThat(m.getEmail()).isEqualTo("bob@example.com");
        assertThat(m.getPhoneNumber()).isEqualTo("2125559999");
        assertThat(m.getCreatedAt()).isEqualTo(T1);
        assertThat(m.getUpdatedAt()).isEqualTo(T2);
    }

    @Test
    void initVersionOnlyFillsAMissingVersion() {
        Member m = Member.register("Ann", "ann@example.com", "2125551212", T1);
        assertThat(m.getVersion()).isNull();

        m.initVersion();
        assertThat(m.getVersion()).isZero();

        ReflectionTestUtils.setField(m, "version", 5L);
        m.initVersion();
        assertThat(m.getVersion()).isEqualTo(5L);
    }

    @Test
    void userStartsEnabledWithGivenFields() {
        User u = new User("jane", "hash", Role.VIEWER, T1);

        assertThat(u.getId()).isNull();
        assertThat(u.getUsername()).isEqualTo("jane");
        assertThat(u.getPasswordHash()).isEqualTo("hash");
        assertThat(u.getRole()).isEqualTo(Role.VIEWER);
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.getCreatedAt()).isEqualTo(T1);
    }

    @Test
    void rolesAreAdminAndViewer() {
        assertThat(Role.values()).containsExactly(Role.ADMIN, Role.VIEWER);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }
}
