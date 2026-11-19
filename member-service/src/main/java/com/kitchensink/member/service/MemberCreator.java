package com.kitchensink.member.service;

import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * The single place where a new member is persisted, shared by self-registration and admin "add member".
 * A duplicate email is rejected up front for a clean error, and again via the unique index to cover races.
 */
@Component
@RequiredArgsConstructor
public class MemberCreator {

    private final MemberRepository memberRepository;

    public Member create(Member member) {
        if (memberRepository.existsByEmail(member.getEmail())) {     // email is already normalized by Member
            throw new DuplicateEmailException();
        }
        try {
            return memberRepository.save(member);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEmailException();
        }
    }
}
