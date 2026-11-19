package com.kitchensink.member.service;

import com.kitchensink.member.exception.AdminDeletionException;
import com.kitchensink.member.exception.AdminPasswordResetException;
import com.kitchensink.member.exception.LastAdminException;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The administrator safety rules, in one place: at least one administrator must remain, and administrators are neither
 * deleted nor password-reset directly. Admins are never deleted directly (demote first),
 * and a demotion is checked before the write and verified after it, because two admins demoted at the same moment
 * could otherwise each see the other still in place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminGuard {

    private final MemberRepository memberRepository;
    private final Clock clock;

    /** Administrators are demoted first, so this rule is enforced in one place. */
    public void rejectAdminDeletion(Member member) {
        if (member.isAdmin()) {
            throw new AdminDeletionException();
        }
    }

    public void rejectAdminDeletion(List<Member> members) {
        if (members.stream().anyMatch(Member::isAdmin)) {
            throw new AdminDeletionException();
        }
    }

    /**
     * An administrator's password is not reset by another administrator: that would let one admin take over another's
     * account. Same rule as deletion: demote first (a logged step), then reset, then promote again.
     */
    public void rejectAdminPasswordReset(Member member) {
        if (member.isAdmin()) {
            throw new AdminPasswordResetException();
        }
    }

    /** Before a demotion: fast rejection in the normal case. */
    public void ensureAnotherAdminRemains() {
        if (memberRepository.countByRole(Role.ADMIN) < 2) {
            throw new LastAdminException();
        }
    }

    /** After a demotion was saved: if no administrator is left, put the demoted one back and reject the request. */
    public void verifyAnAdminRemains(String demotedId) {
        if (memberRepository.countByRole(Role.ADMIN) == 0) {
            restoreAdmin(demotedId);                            // two admins demoted at the same moment
            throw new LastAdminException();
        }
    }

    /** Undoes a demotion that a concurrent demotion turned into "no administrator left". */
    private void restoreAdmin(String id) {
        try {
            Member current = memberRepository.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
            current.changeRole(Role.ADMIN, clock.instant());
            memberRepository.save(current);
        } catch (RuntimeException e) {
            log.error("Could not restore the administrator role for id={}. Fix manually.", id, e);
        }
    }
}
