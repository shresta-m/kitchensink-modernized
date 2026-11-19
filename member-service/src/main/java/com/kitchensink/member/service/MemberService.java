package com.kitchensink.member.service;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberSearchCriteria;
import java.util.List;
import org.springframework.data.domain.Page;

/** Member use cases. Works with commands and domain objects; mapping to and from web DTOs is the controller's job. */
public interface MemberService {

    Page<Member> list(MemberSearchCriteria criteria, String sort, int page, int size);

    Member get(String id);

    /** Every member matching the filters, in the requested order, as a CSV file. */
    CsvExport exportCsv(MemberSearchCriteria criteria, String sort);

    /** Creates a member with a generated temporary password, returned once. */
    CreatedMember create(CreateMemberCommand command);

    /** Lets a self-registered member sign in. Rejected when the member is not waiting for approval. */
    Member approve(String id);

    /** Edits name, email and phone. Does not touch role or password. */
    Member update(String id, UpdateMemberCommand command);

    /** Changes the role. Demoting the last administrator is rejected. */
    Member changeRole(String id, ChangeRoleCommand command);

    /** Replaces the member's password with a new temporary one, returned once. */
    String resetPassword(String id);

    /** Deletes a non-administrator. An administrator must be demoted first. */
    void delete(String id);

    /** Deletes the members that exist and returns how many were removed. Unknown ids are ignored. */
    int bulkDelete(List<String> ids);
}
