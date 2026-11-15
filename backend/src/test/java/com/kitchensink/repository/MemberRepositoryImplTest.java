package com.kitchensink.repository;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.MemberSort;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortDirection;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.model.Member;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Query shape and bulk-error handling, checked against a mocked template (real-database behaviour is in MemberControllerIT). */
class MemberRepositoryImplTest {

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MemberRepositoryImpl repository = new MemberRepositoryImpl(mongo);

    private static Member member(String n) {
        return Member.register(n, n + "@example.com", "2125551212", Instant.EPOCH);
    }

    @Test
    void searchCountsThenFetchesTheRequestedPageWithAStableSort() {
        when(mongo.count(any(Query.class), eq(Member.class))).thenReturn(25L);
        when(mongo.find(any(Query.class), eq(Member.class))).thenReturn(List.of(member("a")));
        PageQuery page = new PageQuery(2, 10, new SortSpec(MemberSort.CREATED_AT, SortDirection.DESC));

        PageResult<Member> result = repository.search(MemberSearchCriteria.of("An.Na"), page);

        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        org.mockito.Mockito.verify(mongo).find(query.capture(), eq(Member.class));
        assertThat(query.getValue().getSkip()).isEqualTo(20);
        assertThat(query.getValue().getLimit()).isEqualTo(10);
        assertThat(query.getValue().getSortObject().toJson()).contains("createdAt").contains("_id");
        // the user's text is regex-quoted and lowercased, never used as a pattern
        assertThat(query.getValue().getQueryObject().toJson()).contains("\\\\Q" ).contains("an.na");
    }

    @Test
    void emptySearchHasNoFilterAndEverySortFieldMapsToAnIndexedColumn() {
        when(mongo.count(any(Query.class), eq(Member.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(Member.class))).thenReturn(List.of());

        for (MemberSort sort : MemberSort.values()) {
            repository.search(MemberSearchCriteria.of(null), new PageQuery(0, 5, new SortSpec(sort, SortDirection.ASC)));
        }

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        org.mockito.Mockito.verify(mongo, org.mockito.Mockito.times(3)).find(query.capture(), eq(Member.class));
        assertThat(query.getAllValues()).allSatisfy(q -> assertThat(q.getQueryObject()).isEmpty());
        assertThat(query.getAllValues().get(0).getSortObject().toJson()).contains("nameLower");
        assertThat(query.getAllValues().get(1).getSortObject().toJson()).contains("email");
    }

    @Test
    void forEachMatchingStreamsAndClosesTheStream() {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        Stream<Member> stream = Stream.of(member("a"), member("b")).onClose(() -> closed.set(true));
        when(mongo.stream(any(Query.class), eq(Member.class))).thenReturn(stream);
        List<Member> seen = new ArrayList<>();

        repository.forEachMatching(MemberSearchCriteria.of(""), SortSpec.DEFAULT, 100, seen::add);

        assertThat(seen).hasSize(2);
        assertThat(closed).isTrue();
    }

    private BulkOperations bulk(BulkWriteResult result) {
        BulkOperations ops = mock(BulkOperations.class);
        when(mongo.bulkOps(BulkMode.UNORDERED, Member.class)).thenReturn(ops);
        when(ops.execute()).thenReturn(result);
        return ops;
    }

    @Test
    void insertBatchSetsVersionsAndReportsTheInsertedCount() {
        BulkWriteResult result = mock(BulkWriteResult.class);
        when(result.getInsertedCount()).thenReturn(2);
        bulk(result);
        List<Member> batch = List.of(member("a"), member("b"));

        BatchInsertResult outcome = repository.insertBatch(batch);

        assertThat(outcome).isEqualTo(new BatchInsertResult(2, List.of()));
        assertThat(batch).allSatisfy(m -> assertThat(m.getVersion()).isZero());
    }

    private static BulkWriteError error(int code, int index) {
        BulkWriteError e = mock(BulkWriteError.class);
        when(e.getCode()).thenReturn(code);
        when(e.getIndex()).thenReturn(index);
        return e;
    }

    @Test
    void duplicateKeyErrorsAreReportedByPositionInsteadOfThrown() {
        BulkWriteResult partial = mock(BulkWriteResult.class);
        when(partial.getInsertedCount()).thenReturn(1);
        BulkOperationException failure = mock(BulkOperationException.class);
        when(failure.getErrors()).thenReturn(List.of(error(11000, 2), error(11000, 0)));
        when(failure.getResult()).thenReturn(partial);
        BulkOperations ops = bulk(null);
        when(ops.execute()).thenThrow(failure);

        BatchInsertResult outcome = repository.insertBatch(List.of(member("a"), member("b"), member("c")));

        assertThat(outcome.inserted()).isEqualTo(1);
        assertThat(outcome.duplicateIndexes()).containsExactly(0, 2);
    }

    @Test
    void otherBulkErrorsAreRethrownSoTheCallerCanIsolateTheBatch() {
        BulkOperationException failure = mock(BulkOperationException.class);
        when(failure.getErrors()).thenReturn(List.of(error(11000, 0), error(121, 1)));
        BulkOperations ops = bulk(null);
        when(ops.execute()).thenThrow(failure);

        assertThatThrownBy(() -> repository.insertBatch(List.of(member("a"), member("b")))).isSameAs(failure);
    }
}
