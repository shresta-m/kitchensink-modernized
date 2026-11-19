package com.kitchensink.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class MemberCreatorTest {

    @Mock MemberRepository repository;
    @InjectMocks MemberCreator creator;

    private final Member member = TestData.member(null, Role.VIEWER, "a@example.com");

    @Test
    void savesWhenEmailIsFree() {
        when(repository.existsByEmail("a@example.com")).thenReturn(false);
        when(repository.save(member)).thenReturn(member);

        assertThat(creator.create(member)).isSameAs(member);
    }

    @Test
    void rejectsKnownEmailBeforeSaving() {
        when(repository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> creator.create(member)).isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void translatesRaceOnUniqueIndex() {
        when(repository.existsByEmail("a@example.com")).thenReturn(false);
        when(repository.save(member)).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> creator.create(member)).isInstanceOf(DuplicateEmailException.class);
    }
}
