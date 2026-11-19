package com.kitchensink.member.repository;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MemberRepository extends MongoRepository<Member, String>, MemberRepositoryCustom {

    Optional<Member> findByEmail(String email);      // pass the normalized (lowercase) email

    boolean existsByEmail(String email);                       // pass the normalized (lowercase) email

    boolean existsByEmailAndIdNot(String email, String id);    // uniqueness check when updating a member

    long countByRole(Role role);
}
