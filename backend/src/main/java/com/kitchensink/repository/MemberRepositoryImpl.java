package com.kitchensink.repository;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortDirection;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.model.Member;
import com.mongodb.bulk.BulkWriteError;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/** MongoTemplate-based implementation of the custom repository fragment. */
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private static final int DUPLICATE_KEY_CODE = 11000;

    private final MongoTemplate mongo;

    public MemberRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public PageResult<Member> search(MemberSearchCriteria criteria, PageQuery page) {
        Query filter = filterQuery(criteria);
        long total = mongo.count(filter, Member.class);
        Query pageQuery = Query.of(filter).with(sortOf(page.sort())).skip(page.offset()).limit(page.size());
        List<Member> content = mongo.find(pageQuery, Member.class);
        return new PageResult<>(content, page.page(), page.size(), total);
    }

    @Override
    public void forEachMatching(MemberSearchCriteria criteria, SortSpec sort, int limit, Consumer<Member> consumer) {
        Query query = Query.of(filterQuery(criteria)).with(sortOf(sort)).limit(limit);
        try (Stream<Member> stream = mongo.stream(query, Member.class)) {
            stream.forEach(consumer);
        }
    }

    @Override
    public BatchInsertResult insertBatch(List<Member> members) {
        members.forEach(Member::initVersion);
        BulkOperations ops = mongo.bulkOps(BulkMode.UNORDERED, Member.class);
        ops.insert(members);
        try {
            return new BatchInsertResult(ops.execute().getInsertedCount(), List.of());
        } catch (BulkOperationException e) {
            boolean onlyDuplicates = e.getErrors().stream().allMatch(err -> err.getCode() == DUPLICATE_KEY_CODE);
            if (!onlyDuplicates) {
                throw e; // a real failure: let the caller isolate this batch
            }
            List<Integer> duplicates = e.getErrors().stream().map(BulkWriteError::getIndex).sorted().toList();
            return new BatchInsertResult(e.getResult().getInsertedCount(), duplicates);
        }
    }

    /**
     * Anchored, case-sensitive prefix match on lowercased fields so MongoDB can use the indexes.
     * User input is quoted with Pattern.quote, so it can never act as a regex.
     */
    private static Query filterQuery(MemberSearchCriteria criteria) {
        Query query = new Query();
        if (!criteria.isEmpty()) {
            String prefix = "^" + Pattern.quote(criteria.normalizedText());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("nameLower").regex(prefix),
                    Criteria.where("email").regex(prefix),
                    Criteria.where("phoneNumber").regex(prefix)));
        }
        return query;
    }

    private static Sort sortOf(SortSpec spec) {
        String field = switch (spec.field()) {
            case NAME -> "nameLower";
            case EMAIL -> "email";
            case CREATED_AT -> "createdAt";
        };
        Sort.Direction dir = spec.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        // _id as tie-breaker keeps pages stable when many rows share the sort key.
        return Sort.by(dir, field).and(Sort.by(Sort.Direction.ASC, "_id"));
    }
}
