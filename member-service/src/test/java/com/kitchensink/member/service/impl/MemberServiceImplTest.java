package com.kitchensink.member.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.exception.AdminPasswordResetException;
import com.kitchensink.member.exception.AdminDeletionException;
import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.exception.ExportTooLargeException;
import com.kitchensink.member.exception.InvalidSortException;
import com.kitchensink.member.exception.LastAdminException;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.member.exception.NotPendingApprovalException;
import com.kitchensink.member.exception.PageTooDeepException;
import com.kitchensink.member.exception.StaleVersionException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import com.kitchensink.member.service.AdminGuard;
import com.kitchensink.member.service.ChangeRoleCommand;
import com.kitchensink.member.service.CreatedMember;
import com.kitchensink.member.service.CsvExport;
import com.kitchensink.member.service.MemberCreator;
import com.kitchensink.member.service.MemberCsvWriter;
import com.kitchensink.member.service.MemberSortParser;
import com.kitchensink.member.service.UpdateMemberCommand;
import com.kitchensink.security.TemporaryPasswordGenerator;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceImplTest {

    private static final MemberSearchCriteria ALL = new MemberSearchCriteria(null, null, null);

    @Mock MemberRepository repository;
    @Mock TemporaryPasswordGenerator generator;
    @Mock PasswordEncoder encoder;

    private MemberServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MemberServiceImpl(repository, new MemberCreator(repository), new MemberSortParser(),
                new MemberCsvWriter(), new AdminGuard(repository, TestData.CLOCK), generator, encoder, TestData.CLOCK);
    }

    private Member viewer() {
        return TestData.member("1", Role.VIEWER, "asha@example.com");
    }

    private Member admin() {
        return TestData.member("2", Role.ADMIN, "admin@example.com");
    }

    // ---------- list / get / export ----------

    @Test
    void listMapsPageAndUsesParsedSort() {
        when(repository.search(eq(ALL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(viewer()), PageRequest.of(0, 8), 1));

        Page<Member> page = service.list(ALL, "email,desc", 0, 8);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getEmail()).isEqualTo("asha@example.com");
        assertThat(page.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(eq(ALL), pageable.capture());
        assertThat(pageable.getValue().getSort().toList().get(0).getProperty()).isEqualTo("email");
    }

    @Test
    void listRejectsPagesBeyondTheOffsetCap() {
        assertThatThrownBy(() -> service.list(ALL, "name,asc", 101, 100)).isInstanceOf(PageTooDeepException.class);
    }

    @Test
    void listAllowsThePageAtTheOffsetCap() {
        when(repository.search(eq(ALL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(100, 100), 0));

        assertThat(service.list(ALL, "name,asc", 100, 100).getContent()).isEmpty();
    }

    @Test
    void listRejectsInvalidSort() {
        assertThatThrownBy(() -> service.list(ALL, "bogus", 0, 8)).isInstanceOf(InvalidSortException.class);
    }

    @Test
    void getReturnsMember() {
        when(repository.findById("1")).thenReturn(Optional.of(viewer()));

        Member response = service.get("1");

        assertThat(response.getId()).isEqualTo("1");
        assertThat(response.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.getPhoneNumber()).isEqualTo("8123456789");
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("x")).isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("x");
    }

    @Test
    void exportBuildsCsvNamedAfterToday() {
        when(repository.searchAll(eq(ALL), any(Sort.class), eq(50_001))).thenReturn(List.of(viewer()));

        CsvExport file = service.exportCsv(ALL, "name,asc");

        assertThat(file.filename()).isEqualTo("members-2026-11-17.csv");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).contains("asha@example.com");
    }

    @Test
    void exportAtTheLimitIsAllowed() {
        when(repository.searchAll(eq(ALL), any(Sort.class), eq(50_001)))
                .thenReturn(Collections.nCopies(50_000, viewer()));

        assertThat(service.exportCsv(ALL, "name,asc").content()).isNotEmpty();
    }

    @Test
    void exportOverTheLimitIsRejected() {
        when(repository.searchAll(eq(ALL), any(Sort.class), eq(50_001)))
                .thenReturn(Collections.nCopies(50_001, viewer()));

        assertThatThrownBy(() -> service.exportCsv(ALL, "name,asc")).isInstanceOf(ExportTooLargeException.class);
    }

    // ---------- create ----------

    @Test
    void createStoresEncodedTemporaryPasswordAndReturnsItOnce() {
        when(generator.generate()).thenReturn("Temp1234");
        when(encoder.encode("Temp1234")).thenReturn("encoded");
        when(repository.existsByEmail("asha@example.com")).thenReturn(false);
        when(repository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatedMember response = service.create(TestData.createCommand(Role.ADMIN));

        assertThat(response.temporaryPassword()).isEqualTo("Temp1234");
        assertThat(response.member().getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.member().getStatus()).isEqualTo(MemberStatus.TEMP_PASSWORD);
        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(generator.generate()).thenReturn("Temp1234");
        when(encoder.encode("Temp1234")).thenReturn("encoded");
        when(repository.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(TestData.createCommand(Role.VIEWER)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    // ---------- update (profile only) ----------

    private UpdateMemberCommand updateRequest() {
        return updateRequest(null);
    }

    private UpdateMemberCommand updateRequest(Long version) {
        return new UpdateMemberCommand(new MemberDetails("Ravi Kumar", "RAVI@example.com", "+91", "8123456789"), version);
    }

    @Test
    void updateChangesProfileButNotRole() {
        Member existing = admin();
        when(repository.findById("2")).thenReturn(Optional.of(existing));
        when(repository.existsByEmailAndIdNot("ravi@example.com", "2")).thenReturn(false);
        when(repository.save(existing)).thenReturn(existing);

        Member response = service.update("2", updateRequest());

        assertThat(response.getName()).isEqualTo("Ravi Kumar");
        assertThat(response.getEmail()).isEqualTo("ravi@example.com");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.getUpdatedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void updateRejectsEmailUsedByAnotherMember() {
        when(repository.findById("1")).thenReturn(Optional.of(viewer()));
        when(repository.existsByEmailAndIdNot("ravi@example.com", "1")).thenReturn(true);

        assertThatThrownBy(() -> service.update("1", updateRequest())).isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void updateTranslatesRaceOnUniqueIndex() {
        Member existing = viewer();
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(repository.existsByEmailAndIdNot("ravi@example.com", "1")).thenReturn(false);
        when(repository.save(existing)).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.update("1", updateRequest())).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateWithAStaleVersionIsRejected() {
        Member existing = viewer();
        ReflectionTestUtils.setField(existing, "version", 3L);
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        UpdateMemberCommand request = updateRequest(2L);

        assertThatThrownBy(() -> service.update("1", request)).isInstanceOf(StaleVersionException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("x", updateRequest())).isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- change role ----------

    private static ChangeRoleCommand role(Role role) {
        return new ChangeRoleCommand(role, null);
    }

    @Test
    void demotingAnAdminIsAllowedWhenAnotherRemains() {
        Member existing = admin();
        when(repository.findById("2")).thenReturn(Optional.of(existing));
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L, 1L);     // before and after the change
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.changeRole("2", role(Role.VIEWER)).getRole()).isEqualTo(Role.VIEWER);
    }

    @Test
    void demotingTheLastAdminIsRejectedBeforeAnyWrite() {
        when(repository.findById("2")).thenReturn(Optional.of(admin()));
        when(repository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole("2", role(Role.VIEWER))).isInstanceOf(LastAdminException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void twoAdminsDemotedAtOnceAreRestoredAndRejected() {
        Member existing = admin();
        when(repository.findById("2")).thenReturn(Optional.of(existing));
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L, 0L);     // the other admin was demoted in between
        when(repository.save(existing)).thenReturn(existing);

        assertThatThrownBy(() -> service.changeRole("2", role(Role.VIEWER))).isInstanceOf(LastAdminException.class);

        assertThat(existing.isAdmin()).isTrue();                          // role put back
        verify(repository, times(2)).save(existing);
    }

    @Test
    void failedRestoreStillRejectsTheRequest() {
        Member existing = admin();
        when(repository.findById("2")).thenReturn(Optional.of(existing), Optional.empty());
        when(repository.countByRole(Role.ADMIN)).thenReturn(2L, 0L);
        when(repository.save(existing)).thenReturn(existing);

        assertThatThrownBy(() -> service.changeRole("2", role(Role.VIEWER))).isInstanceOf(LastAdminException.class);
        verify(repository, times(1)).save(existing);
    }

    @Test
    void keepingAnAdminAdminNeedsNoCheck() {
        Member existing = admin();
        when(repository.findById("2")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.changeRole("2", role(Role.ADMIN)).getRole()).isEqualTo(Role.ADMIN);
        verify(repository, never()).countByRole(any(Role.class));
    }

    @Test
    void promotingAViewerNeedsNoCheck() {
        Member existing = viewer();
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        assertThat(service.changeRole("1", role(Role.ADMIN)).getRole()).isEqualTo(Role.ADMIN);
        verify(repository, never()).countByRole(any(Role.class));
    }

    @Test
    void changeRoleWithAStaleVersionIsRejected() {
        Member existing = viewer();
        ReflectionTestUtils.setField(existing, "version", 3L);
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        ChangeRoleCommand request = new ChangeRoleCommand(Role.ADMIN, 2L);

        assertThatThrownBy(() -> service.changeRole("1", request)).isInstanceOf(StaleVersionException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void changeRoleThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole("x", role(Role.ADMIN))).isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- reset password ----------

    @Test
    void resetPasswordIssuesNewTemporaryPassword() {
        Member existing = viewer();
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(generator.generate()).thenReturn("Temp5678");
        when(encoder.encode("Temp5678")).thenReturn("encoded2");

        String temporaryPassword = service.resetPassword("1");

        assertThat(temporaryPassword).isEqualTo("Temp5678");
        assertThat(existing.getPasswordHash()).isEqualTo("encoded2");
        assertThat(existing.isMustChangePassword()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void resetPasswordSignsTheMemberOutEverywhere() {
        Member existing = viewer();
        int before = existing.getTokenVersion();
        when(repository.findById("1")).thenReturn(Optional.of(existing));
        when(generator.generate()).thenReturn("Temp5678");
        when(encoder.encode("Temp5678")).thenReturn("encoded2");

        service.resetPassword("1");

        assertThat(existing.getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void anAdministratorsPasswordCannotBeReset() {
        when(repository.findById("2")).thenReturn(Optional.of(admin()));

        assertThatThrownBy(() -> service.resetPassword("2")).isInstanceOf(AdminPasswordResetException.class);
        verify(repository, never()).save(any(Member.class));
        verifyNoInteractions(generator);
    }

    @Test
    void resetPasswordThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("x")).isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- delete ----------

    // ---------- approve ----------

    @Test
    void approveLetsAPendingMemberSignIn() {
        Member pending = Member.register(TestData.details("p@example.com"), Role.VIEWER, "hash", true, TestData.NOW);
        when(repository.findById("5")).thenReturn(Optional.of(pending));
        when(repository.save(pending)).thenReturn(pending);

        Member approved = service.approve("5");

        assertThat(approved.isPendingApproval()).isFalse();
        assertThat(approved.getUpdatedAt()).isEqualTo(TestData.NOW);
        verify(repository).save(pending);
    }

    @Test
    void approveRejectsAMemberWhoIsNotWaiting() {
        when(repository.findById("1")).thenReturn(Optional.of(viewer()));

        assertThatThrownBy(() -> service.approve("1")).isInstanceOf(NotPendingApprovalException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void approveThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve("x")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void deleteRemovesAViewer() {
        Member existing = viewer();
        when(repository.findById("1")).thenReturn(Optional.of(existing));

        service.delete("1");

        verify(repository).delete(existing);
    }

    @Test
    void administratorsMustBeDemotedBeforeDeletion() {
        when(repository.findById("2")).thenReturn(Optional.of(admin()));

        assertThatThrownBy(() -> service.delete("2")).isInstanceOf(AdminDeletionException.class);
        verify(repository, never()).delete(any(Member.class));
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("x")).isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- bulk delete ----------

    @Test
    void bulkDeleteRemovesFoundMembersWithOneCommandAndIgnoresUnknownIds() {
        when(repository.findAllById(anyIterable()))
                .thenReturn(List.of(viewer(), TestData.member("3", Role.VIEWER, "b@example.com")));
        when(repository.removeByIds(List.of("1", "3"))).thenReturn(2L);

        int deleted = service.bulkDelete(List.of("1", "3", "3", "unknown"));

        assertThat(deleted).isEqualTo(2);
    }

    @Test
    void bulkDeleteRefusesWhenAnAdministratorIsSelected() {
        when(repository.findAllById(anyIterable())).thenReturn(List.of(viewer(), admin()));

        assertThatThrownBy(() -> service.bulkDelete(List.of("1", "2"))).isInstanceOf(AdminDeletionException.class);
        verify(repository, never()).removeByIds(anyCollection());
    }
}
