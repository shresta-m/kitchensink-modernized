package com.kitchensink.member.repository;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberSearchCriteria;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Queries that Spring Data cannot derive from method names. Implemented by MemberRepositoryCustomImpl. */
public interface MemberRepositoryCustom {
    Page<Member> search(MemberSearchCriteria criteria, Pageable pageable);

    /** All matching members in the given order, capped at {@code limit} rows. Used by the CSV export. */
    List<Member> searchAll(MemberSearchCriteria criteria, Sort sort, int limit);

    /** Deletes every member with one of the ids in a single command and returns how many were removed. */
    long removeByIds(Collection<String> ids);
}
