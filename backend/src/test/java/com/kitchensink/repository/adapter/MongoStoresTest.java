package com.kitchensink.repository.adapter;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.exception.domain.DuplicateEmailException;
import com.kitchensink.exception.domain.DuplicateUsernameException;
import com.kitchensink.exception.domain.VersionConflictException;
import com.kitchensink.model.Member;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.repository.MemberRepository;
import com.kitchensink.repository.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

/** The adapters are the only code allowed to see persistence exceptions; they must translate every one. */
class MongoStoresTest {

    private final MemberRepository memberRepo = mock(MemberRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final MongoMemberStore members = new MongoMemberStore(memberRepo);
    private final MongoUserStore users = new MongoUserStore(userRepo);

    private static Member member() {
        return Member.register("Ann", "ann@example.com", "2125551212", Instant.EPOCH);
    }

    @Test
    void memberInsertMapsDuplicateKeyToDuplicateEmailKeepingTheCause() {
        DuplicateKeyException cause = new DuplicateKeyException("E11000");
        when(memberRepo.insert(any(Member.class))).thenThrow(cause);

        assertThatThrownBy(() -> members.insert(member()))
                .isInstanceOf(DuplicateEmailException.class)
                .hasCause(cause);
    }

    @Test
    void memberSaveMapsDuplicateKeyAndOptimisticLockFailure() {
        when(memberRepo.save(any(Member.class)))
                .thenThrow(new DuplicateKeyException("E11000"))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(() -> members.save(member())).isInstanceOf(DuplicateEmailException.class);
        assertThatThrownBy(() -> members.save(member())).isInstanceOf(VersionConflictException.class);
    }

    @Test
    void successfulWritesPassThrough() {
        Member m = member();
        when(memberRepo.insert(m)).thenReturn(m);
        assertThat(members.insert(m)).isSameAs(m);
    }

    @Test
    void userInsertMapsDuplicateKeyToDuplicateUsername() {
        when(userRepo.insert(any(User.class))).thenThrow(new DuplicateKeyException("E11000"));

        assertThatThrownBy(() -> users.insert(new User("taken", "hash", Role.VIEWER, Instant.EPOCH)))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void readsAndDeletesDelegateToTheRepository() {
        Member m = member();
        when(memberRepo.findById("1")).thenReturn(java.util.Optional.of(m));
        when(memberRepo.existsById("1")).thenReturn(true);
        when(memberRepo.findAllById(java.util.List.of("1"))).thenReturn(java.util.List.of(m));

        assertThat(members.findById("1")).contains(m);
        assertThat(members.existsById("1")).isTrue();
        assertThat(members.findAllById(java.util.List.of("1"))).containsExactly(m);
        members.deleteById("1");
        members.deleteAllById(java.util.List.of("1"));
        org.mockito.Mockito.verify(memberRepo).deleteById("1");
        org.mockito.Mockito.verify(memberRepo).deleteAllById(java.util.List.of("1"));
    }

    @Test
    void searchStreamingAndBulkInsertDelegate() {
        var criteria = com.kitchensink.dto.member.MemberSearchCriteria.of("a");
        var page = new com.kitchensink.dto.member.PageQuery(0, 10, com.kitchensink.dto.member.SortSpec.DEFAULT);
        var result = new com.kitchensink.dto.common.PageResult<Member>(java.util.List.of(), 0, 10, 0);
        var batch = new com.kitchensink.dto.imports.BatchInsertResult(1, java.util.List.of());
        when(memberRepo.search(criteria, page)).thenReturn(result);
        when(memberRepo.insertBatch(java.util.List.of())).thenReturn(batch);
        java.util.function.Consumer<Member> consumer = x -> { };

        assertThat(members.search(criteria, page)).isSameAs(result);
        assertThat(members.insertBatch(java.util.List.of())).isSameAs(batch);
        members.forEachMatching(criteria, com.kitchensink.dto.member.SortSpec.DEFAULT, 5, consumer);
        org.mockito.Mockito.verify(memberRepo).forEachMatching(criteria, com.kitchensink.dto.member.SortSpec.DEFAULT, 5, consumer);
    }

    @Test
    void userLookupsDelegateToTheRepository() {
        User u = new User("jane", "h", Role.VIEWER, Instant.EPOCH);
        when(userRepo.findByUsername("jane")).thenReturn(java.util.Optional.of(u));
        when(userRepo.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(java.util.List.of(u));

        assertThat(users.findByUsername("jane")).contains(u);
        assertThat(users.findAllOrderedByUsername()).containsExactly(u);
    }
}
