package com.kitchensink.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.Role;
import com.mongodb.client.result.DeleteResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class MemberRepositoryCustomImplTest {

    private static final MemberSearchCriteria ALL = new MemberSearchCriteria(null, null, null);

    private final MongoTemplate template = mock(MongoTemplate.class);
    private final MemberRepositoryCustomImpl repository = new MemberRepositoryCustomImpl(template, new MemberQueryBuilder());
    private final Member member = TestData.member("1", Role.VIEWER, "a@example.com");

    private List<Member> members(int n) {
        return Collections.nCopies(n, member);
    }

    @Test
    void middlePageCountsTheTotalWithoutPagingAndAppliesPageAndSort() {
        when(template.count(any(Query.class), eq(Member.class))).thenReturn(11L);
        when(template.find(any(Query.class), eq(Member.class))).thenReturn(members(5));

        Page<Member> page = repository.search(ALL, PageRequest.of(1, 5, Sort.by("nameLower")));

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(5);
        ArgumentCaptor<Query> paged = ArgumentCaptor.forClass(Query.class);
        verify(template).find(paged.capture(), eq(Member.class));
        assertThat(paged.getValue().getSkip()).isEqualTo(5);
        assertThat(paged.getValue().getLimit()).isEqualTo(5);
        assertThat(paged.getValue().getSortObject().toString()).contains("nameLower");
        ArgumentCaptor<Query> counted = ArgumentCaptor.forClass(Query.class);
        verify(template).count(counted.capture(), eq(Member.class));
        assertThat(counted.getValue().getLimit()).isZero();                  // the count query is not paged
        assertThat(counted.getValue().getSkip()).isZero();
    }

    @Test
    void shortFirstPageSkipsTheCountQuery() {
        when(template.find(any(Query.class), eq(Member.class))).thenReturn(members(3));

        Page<Member> page = repository.search(ALL, PageRequest.of(0, 5));

        assertThat(page.getTotalElements()).isEqualTo(3);
        verify(template, never()).count(any(Query.class), eq(Member.class));
    }

    @Test
    void shortLastPageSkipsTheCountQuery() {
        when(template.find(any(Query.class), eq(Member.class))).thenReturn(members(2));

        Page<Member> page = repository.search(ALL, PageRequest.of(2, 5));

        assertThat(page.getTotalElements()).isEqualTo(12);
        verify(template, never()).count(any(Query.class), eq(Member.class));
    }

    @Test
    void searchAllAppliesSortAndLimit() {
        when(template.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member));

        List<Member> rows = repository.searchAll(ALL, Sort.by(Sort.Direction.DESC, "email"), 7);

        assertThat(rows).containsExactly(member);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).find(query.capture(), eq(Member.class));
        assertThat(query.getValue().getLimit()).isEqualTo(7);
        assertThat(query.getValue().getSortObject().toString()).contains("email");
    }

    @Test
    void removeByIdsDeletesWithOneCommandAndReturnsTheCount() {
        when(template.remove(any(Query.class), eq(Member.class))).thenReturn(DeleteResult.acknowledged(2));

        assertThat(repository.removeByIds(List.of("1", "2"))).isEqualTo(2);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(template).remove(query.capture(), eq(Member.class));
        assertThat(query.getValue().getQueryObject().toString()).contains("_id", "$in", "role", "$ne");
    }
}
