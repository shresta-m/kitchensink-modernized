package com.kitchensink.service;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.UpdateMemberRequest;
import com.kitchensink.exception.domain.MemberNotFoundException;
import com.kitchensink.exception.domain.VersionConflictException;
import com.kitchensink.model.Member;
import com.kitchensink.service.impl.MemberServiceImpl;
import com.kitchensink.service.port.MemberActivityMetrics;
import com.kitchensink.service.port.MemberStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberStore repository;
    @Mock MemberActivityMetrics metrics;

    private final Instant now = Instant.parse("2026-11-13T10:00:00Z");
    private MemberServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MemberServiceImpl(repository, metrics, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Member existing(String id, long version) {
        Member m = Member.register("Old", "old@example.com", "2125551212", Instant.EPOCH);
        ReflectionTestUtils.setField(m, "id", id);
        ReflectionTestUtils.setField(m, "version", version);
        return m;
    }

    @Test
    void createStoresNormalizedMemberAndCountsIt() {
        when(repository.insert(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member saved = service.create(new MemberRequest("Ann", "ANN@Example.com", "2125551212"));

        assertThat(saved.getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        verify(metrics).memberCreated();
    }

    @Test
    void updateAppliesDetailsKeepingCreatedAtAndStampingUpdatedAt() {
        when(repository.findById("id1")).thenReturn(Optional.of(existing("id1", 3)));
        when(repository.save(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        Member updated = service.update("id1", new UpdateMemberRequest("New", "new@example.com", "2125559999", 3L));

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getCreatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(updated.getUpdatedAt()).isEqualTo(now);
        verify(metrics).memberUpdated();
    }

    @Test
    void updateWithStaleVersionIsRejectedBeforeWriting() {
        when(repository.findById("id1")).thenReturn(Optional.of(existing("id1", 4)));

        assertThatThrownBy(() -> service.update("id1", new UpdateMemberRequest("N", "n@example.com", "2125559999", 3L)))
                .isInstanceOf(VersionConflictException.class);
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void updateOfUnknownMemberIsNotFound() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("nope", new UpdateMemberRequest("N", "n@example.com", "2125559999", 0L)))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void deleteOfUnknownMemberIsNotFound() {
        when(repository.existsById("nope")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("nope")).isInstanceOf(MemberNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void bulkDeleteReportsWhichIdsWereNotFoundAndIgnoresRepeats() {
        when(repository.findAllById(any())).thenReturn(List.of(existing("a", 0)));

        BulkDeleteResponse result = service.bulkDelete(List.of("a", "b", "a"));

        verify(repository).deleteAllById(java.util.Set.of("a"));
        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.notFound()).containsExactly("b");
    }
}
