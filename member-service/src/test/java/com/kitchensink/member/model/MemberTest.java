package com.kitchensink.member.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.TestData;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberTest {

    private static final Instant LATER = TestData.NOW.plusSeconds(60);

    @Test
    void registerNormalizesAndStoresFields() {
        Member m = Member.register(new MemberDetails("  Asha   Rao ", " Asha@Example.com ", "91", "81234 56789"),
                Role.VIEWER, "hash", false, TestData.NOW);

        assertThat(m.getName()).isEqualTo("Asha Rao");
        assertThat(m.getNameLower()).isEqualTo("asha rao");
        assertThat(m.getEmail()).isEqualTo("asha@example.com");
        assertThat(m.getCountryCode()).isEqualTo("+91");
        assertThat(m.getPhoneNumber()).isEqualTo("8123456789");
        assertThat(m.getRole()).isEqualTo(Role.VIEWER);
        assertThat(m.getPasswordHash()).isEqualTo("hash");
        assertThat(m.isMustChangePassword()).isFalse();
        assertThat(m.getCreatedAt()).isEqualTo(TestData.NOW);
        assertThat(m.getUpdatedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void createByAdminRequiresPasswordChange() {
        Member m = Member.createByAdmin(TestData.details("a@example.com"), Role.ADMIN, "temp", TestData.NOW);
        assertThat(m.isMustChangePassword()).isTrue();
        assertThat(m.isAdmin()).isTrue();
    }

    @Test
    void factoriesRejectMissingRoleAndBlankFields() {
        assertThatThrownBy(() -> Member.register(TestData.details("a@example.com"), null, "h", false, TestData.NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Member.register(new MemberDetails(" ", "a@example.com", "+91", "8123456789"),
                Role.VIEWER, "h", false, TestData.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProfileKeepsRoleAndPassword() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);

        m.updateProfile(new MemberDetails("Ravi Kumar", "RAVI@example.com", "+44", "7400123456"), LATER);

        assertThat(m.getName()).isEqualTo("Ravi Kumar");
        assertThat(m.getNameLower()).isEqualTo("ravi kumar");
        assertThat(m.getEmail()).isEqualTo("ravi@example.com");
        assertThat(m.getCountryCode()).isEqualTo("+44");
        assertThat(m.getRole()).isEqualTo(Role.VIEWER);
        assertThat(m.getPasswordHash()).isEqualTo("hash");
        assertThat(m.getCreatedAt()).isEqualTo(TestData.NOW);
        assertThat(m.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void changeRoleUpdatesRoleAndTimestamp() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);
        assertThat(m.isAdmin()).isFalse();

        m.changeRole(Role.ADMIN, LATER);

        assertThat(m.isAdmin()).isTrue();
        assertThat(m.getUpdatedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> m.changeRole(null, LATER)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void passwordLifecycle() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);

        m.issueTemporaryPassword("temp", LATER);
        assertThat(m.getPasswordHash()).isEqualTo("temp");
        assertThat(m.isMustChangePassword()).isTrue();

        m.changePassword("mine", LATER.plusSeconds(1));
        assertThat(m.getPasswordHash()).isEqualTo("mine");
        assertThat(m.isMustChangePassword()).isFalse();
        assertThat(m.getUpdatedAt()).isEqualTo(LATER.plusSeconds(1));
    }

    @Test
    void everySessionEndingEventBumpsTheTokenVersion() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);
        assertThat(m.getTokenVersion()).isZero();

        m.issueTemporaryPassword("temp", LATER);
        assertThat(m.getTokenVersion()).isEqualTo(1);       // an admin reset signs the member out
        m.changePassword("mine", LATER);
        assertThat(m.getTokenVersion()).isEqualTo(2);       // so does choosing a new password
        m.revokeSessions(LATER.plusSeconds(5));
        assertThat(m.getTokenVersion()).isEqualTo(3);       // and so does signing out
        assertThat(m.getUpdatedAt()).isEqualTo(LATER.plusSeconds(5));
    }

    @Test
    void profileAndRoleChangesKeepExistingSessions() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);

        m.updateProfile(TestData.details("b@example.com"), LATER);
        m.changeRole(Role.ADMIN, LATER);

        assertThat(m.getTokenVersion()).isZero();            // the role is read live, so no revocation is needed
    }

    @Test
    void statusIsDerivedFromTheFlagsAndAPendingMemberShowsAsPendingFirst() {
        Member active = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", false, TestData.NOW);
        Member temp = Member.createByAdmin(TestData.details("b@example.com"), Role.VIEWER, "temp", TestData.NOW);
        Member pending = Member.register(TestData.details("c@example.com"), Role.VIEWER, "hash", true, TestData.NOW);
        ReflectionTestUtils.setField(pending, "mustChangePassword", true);          // both flags set: pending wins

        assertThat(active.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(temp.getStatus()).isEqualTo(MemberStatus.TEMP_PASSWORD);
        assertThat(pending.getStatus()).isEqualTo(MemberStatus.PENDING_APPROVAL);
        assertThat(MemberStatus.PENDING_APPROVAL.label()).isEqualTo("Pending approval");
    }

    @Test
    void registrationCanWaitForApprovalAndApprovalClearsIt() {
        Member m = Member.register(TestData.details("a@example.com"), Role.VIEWER, "hash", true, TestData.NOW);
        assertThat(m.isPendingApproval()).isTrue();

        m.approve(LATER);

        assertThat(m.isPendingApproval()).isFalse();
        assertThat(m.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(m.getUpdatedAt()).isEqualTo(LATER);
        assertThat(m.getTokenVersion()).isZero();
    }

    @Test
    void membersCreatedByAnAdminAreNeverPending() {
        Member m = Member.createByAdmin(TestData.details("a@example.com"), Role.VIEWER, "temp", TestData.NOW);

        assertThat(m.isPendingApproval()).isFalse();
    }
}
