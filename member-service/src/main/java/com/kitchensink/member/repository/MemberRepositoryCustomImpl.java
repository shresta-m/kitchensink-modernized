package com.kitchensink.member.repository;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberSearchCriteria;
import java.util.Collection;
import java.util.List;

import com.kitchensink.member.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@RequiredArgsConstructor
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {

    private final MongoTemplate mongoTemplate;
    private final MemberQueryBuilder queryBuilder;

    @Override
    public Page<Member> search(MemberSearchCriteria criteria, Pageable pageable) {
        List<Member> content = mongoTemplate.find(queryBuilder.build(criteria).with(pageable), Member.class);
        // The total is only counted when the page alone cannot tell it (a short first or last page needs no count).
        // The count uses its own query because paging (skip/limit) must not be applied to it.
        return PageableExecutionUtils.getPage(content, pageable,
                () -> mongoTemplate.count(queryBuilder.build(criteria), Member.class));
    }

    @Override
    public List<Member> searchAll(MemberSearchCriteria criteria, Sort sort, int limit) {
        Query query = queryBuilder.build(criteria).with(sort).limit(limit);
        return mongoTemplate.find(query, Member.class);
    }

    /**
     * Deletes the given ids, never an administrator. The role filter is part of the delete itself, so a member
     * promoted after the service-level check is skipped instead of removed. The count can therefore be lower than
     * the number of ids.
     */
    @Override
    public long removeByIds(Collection<String> ids) {
        Query query = Query.query(Criteria.where("_id").in(ids).and("role").ne(Role.ADMIN));
        return mongoTemplate.remove(query, Member.class).getDeletedCount();
    }
}
