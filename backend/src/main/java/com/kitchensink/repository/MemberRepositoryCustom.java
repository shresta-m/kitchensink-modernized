package com.kitchensink.repository;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.model.Member;
import java.util.List;
import java.util.function.Consumer;

public interface MemberRepositoryCustom {

    PageResult<Member> search(MemberSearchCriteria criteria, PageQuery page);

    /** Streams matching members to the consumer without loading them all into memory. */
    void forEachMatching(MemberSearchCriteria criteria, SortSpec sort, int limit, Consumer<Member> consumer);

    /** Unordered bulk insert; unique-index violations are reported per position, not thrown. */
    BatchInsertResult insertBatch(List<Member> members);
}
