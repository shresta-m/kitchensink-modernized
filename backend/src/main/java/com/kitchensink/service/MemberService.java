package com.kitchensink.service;



import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.UpdateMemberRequest;
import com.kitchensink.model.Member;
import java.util.List;

public interface MemberService {

    Member create(MemberRequest request);

    Member get(String id);

    PageResult<Member> search(String text, PageQuery page);

    /** @throws com.kitchensink.exception.domain.VersionConflictException if the member changed since it was loaded */
    Member update(String id, UpdateMemberRequest request);

    void delete(String id);

    BulkDeleteResponse bulkDelete(List<String> ids);
}
