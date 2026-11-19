package com.kitchensink.member.service.impl;

import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.exception.ExportTooLargeException;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.member.exception.NotPendingApprovalException;
import com.kitchensink.member.exception.PageTooDeepException;
import com.kitchensink.member.exception.StaleVersionException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import com.kitchensink.member.service.AdminGuard;
import com.kitchensink.member.service.ChangeRoleCommand;
import com.kitchensink.member.service.CreateMemberCommand;
import com.kitchensink.member.service.CreatedMember;
import com.kitchensink.member.service.CsvExport;
import com.kitchensink.member.service.MemberCreator;
import com.kitchensink.member.service.MemberCsvWriter;
import com.kitchensink.member.service.MemberService;
import com.kitchensink.member.service.MemberSortParser;
import com.kitchensink.member.service.UpdateMemberCommand;
import com.kitchensink.member.validation.MemberRules;
import com.kitchensink.security.TemporaryPasswordGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    /** The export is built in memory, so it is capped. Larger exports need narrower filters. */
    private static final int MAX_EXPORT_ROWS = 50_000;

    /** Deep pages make MongoDB skip every earlier row, so how far a page may reach is capped. */
    private static final int MAX_OFFSET = 10_000;

    private final MemberRepository memberRepository;
    private final MemberCreator memberCreator;
    private final MemberSortParser sortParser;
    private final MemberCsvWriter csvWriter;
    private final AdminGuard adminGuard;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    public Page<Member> list(MemberSearchCriteria criteria, String sort, int page, int size) {
        if ((long) page * size > MAX_OFFSET) {
            throw new PageTooDeepException(MAX_OFFSET);
        }
        PageRequest pageable = PageRequest.of(page, size, sortParser.parse(sort));
        return memberRepository.search(criteria, pageable);
    }

    @Override
    public Member get(String id) {
        return findOrThrow(id);
    }

    @Override
    public CsvExport exportCsv(MemberSearchCriteria criteria, String sort) {
        Sort order = sortParser.parse(sort);
        List<Member> rows = memberRepository.searchAll(criteria, order, MAX_EXPORT_ROWS + 1);   // +1 detects overflow
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new ExportTooLargeException(MAX_EXPORT_ROWS);
        }
        return new CsvExport("members-" + LocalDate.now(clock) + ".csv", csvWriter.write(rows));
    }

    @Override
    public CreatedMember create(CreateMemberCommand command) {
        String temporaryPassword = passwordGenerator.generate();
        Member member = Member.createByAdmin(
                command.details(),
                command.role(),
                passwordEncoder.encode(temporaryPassword),
                clock.instant());

        Member saved = memberCreator.create(member);
        return new CreatedMember(saved, temporaryPassword);
    }

    @Override
    public Member approve(String id) {
        Member member = findOrThrow(id);
        if (!member.isPendingApproval()) {
            throw new NotPendingApprovalException(id);
        }
        member.approve(clock.instant());
        Member saved = memberRepository.save(member);           // @Version: a concurrent change fails with a 409
        return saved;
    }

    @Override
    public Member update(String id, UpdateMemberCommand command) {
        Member member = findOrThrow(id);
        requireCurrentVersion(member, command.version());
        MemberDetails details = command.details();
        String email = MemberRules.normalizeEmail(details.email());

        if (memberRepository.existsByEmailAndIdNot(email, id)) {
            throw new DuplicateEmailException();
        }
        member.updateProfile(details, clock.instant());
        try {
            Member saved = memberRepository.save(member);       // @Version turns a concurrent edit into a 409
            return saved;
        } catch (DuplicateKeyException e) {                     // lost a race on the unique email index
            throw new DuplicateEmailException();
        }
    }

    @Override
    public Member changeRole(String id, ChangeRoleCommand command) {
        Member member = findOrThrow(id);
        requireCurrentVersion(member, command.version());
        Role newRole = command.role();
        boolean demotion = member.isAdmin() && newRole != Role.ADMIN;
        if (demotion) {
            adminGuard.ensureAnotherAdminRemains();             // fast rejection in the normal case
        }
        member.changeRole(newRole, clock.instant());
        Member saved = memberRepository.save(member);           // @Version: a concurrent change fails with a 409
        if (demotion) {
            adminGuard.verifyAnAdminRemains(id);                // two admins demoted at the same moment
        }
        return saved;
    }

    @Override
    public String resetPassword(String id) {
        Member member = findOrThrow(id);
        adminGuard.rejectAdminPasswordReset(member);
        String temporaryPassword = passwordGenerator.generate();
        member.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), clock.instant());
        memberRepository.save(member);
        return temporaryPassword;
    }

    @Override
    public void delete(String id) {
        Member member = findOrThrow(id);
        adminGuard.rejectAdminDeletion(member);
        memberRepository.delete(member);                        // versioned: fails if the member changed meanwhile
    }

    @Override
    public int bulkDelete(List<String> ids) {
        List<Member> found = memberRepository.findAllById(new LinkedHashSet<>(ids));   // de-duplicates the ids
        adminGuard.rejectAdminDeletion(found);
        long removed = memberRepository.removeByIds(found.stream().map(Member::getId).toList());
        return Math.toIntExact(removed);
    }

    private Member findOrThrow(String id) {
        return memberRepository.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    /** The client edited an older copy: refuse instead of silently overwriting someone else's change. */
    private static void requireCurrentVersion(Member member, Long expectedVersion) {
        if (!Objects.equals(member.getVersion(), expectedVersion)) {
            throw new StaleVersionException();
        }
    }
}
