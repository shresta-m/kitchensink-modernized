package com.kitchensink.controller;



import com.kitchensink.controller.contract.MemberControllerInterface;
import com.kitchensink.dto.common.PageResponse;
import com.kitchensink.dto.member.BulkDeleteRequest;
import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberResponse;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.dto.member.UpdateMemberRequest;
import com.kitchensink.model.Member;
import com.kitchensink.service.MemberService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** HTTP only: translates requests to service calls and back. No business rules here. */
@RestController
public class MemberController implements MemberControllerInterface {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @Override
    public PageResponse<MemberResponse> list(String q, int page, int size, String sort) {
        SortSpec spec = SortSpec.parse(sort);
        return PageResponse.from(members.search(q, new PageQuery(page, size, spec)), MemberResponse::from,
                spec.toParam());
    }

    @Override
    public MemberResponse get(String id) {
        return MemberResponse.from(members.get(id));
    }

    @Override
    public ResponseEntity<MemberResponse> create(MemberRequest body) {
        Member created = members.create(body);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.getId());
        return ResponseEntity.created(location).body(MemberResponse.from(created));
    }

    @Override
    public MemberResponse update(String id, UpdateMemberRequest body) {
        return MemberResponse.from(members.update(id, body));
    }

    @Override
    public ResponseEntity<Void> delete(String id) {
        members.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public BulkDeleteResponse bulkDelete(BulkDeleteRequest body) {
        return members.bulkDelete(body.ids());
    }
}
