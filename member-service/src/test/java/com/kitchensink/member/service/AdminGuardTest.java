package com.kitchensink.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.exception.AdminDeletionException;
import com.kitchensink.member.exception.AdminPasswordResetException;
import com.kitchensink.member.exception.LastAdminException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminGuardTest {

    @Mock MemberRepository repository;

    private AdminGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AdminGuard(repository, TestData.CLOCK);
    }

    private static Member admin() {
        return TestData.member("2", Role.ADMIN, "admin@example.com");
    }

    private static Member viewer() {
        return TestData.member("1", Role.VIEWER, "asha@example.com");
    }

    @Test
    void aSingleViewerMayBeDeleted() {
        assertThatCode(() -> guard.rejectAdminDeletion(viewer())).doesNotThrowAnyException();
    }

    @Test
    void aSingleAdminMayNotBeDeleted() {
        assertThatThrownBy(() -> guard.rejectAdminDeletion(admin())).isInstanceOf(AdminDeletionException.class);
    }

    @Test
    void aListOfViewersMayBeDeleted() {
        assertThatCode(() -> guard.rejectAdminDeletion(List.of(viewer(), viewer()))).doesNotThrowAnyException();
    }

    @Test
    void aListContainingAnAdminIsRejected() {
        assertThatThrownBy(() -> guard.rejectAdminDeletion(List.of(viewer(), admin())))
                .isInstanceOf(AdminDeletionException.class);
    }

    @Test
    void demotionNeedsAnotherAdminToRemain() {
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L);

        assertThatCode(guard::ensureAnotherAdminRemains).doesNotThrowAnyException();
    }

    @Test
    void demotingTheOnlyAdminIsRejected() {
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(guard::ensureAnotherAdminRemains).isInstanceOf(LastAdminException.class);
    }

    @Test
    void verificationPassesWhenAnAdminIsLeft() {
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatCode(() -> guard.verifyAnAdminRemains("2")).doesNotThrowAnyException();
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void verificationRestoresTheAdminWhenNoneIsLeft() {
        Member demoted = viewer();
        when(repository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(repository.findById("1")).thenReturn(Optional.of(demoted));

        assertThatThrownBy(() -> guard.verifyAnAdminRemains("1")).isInstanceOf(LastAdminException.class);

        assertThat(demoted.isAdmin()).isTrue();
        verify(repository).save(demoted);
    }

    @Test
    void failedRestoreIsLoggedAndStillRejects() {
        when(repository.countByRole(Role.ADMIN)).thenReturn(0L);
        when(repository.findById("1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.verifyAnAdminRemains("1")).isInstanceOf(LastAdminException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void anAdministratorsPasswordCannotBeResetButAViewersCan() {
        assertThatThrownBy(() -> guard.rejectAdminPasswordReset(admin()))
                .isInstanceOf(AdminPasswordResetException.class);
        assertThatCode(() -> guard.rejectAdminPasswordReset(viewer())).doesNotThrowAnyException();
    }
}
