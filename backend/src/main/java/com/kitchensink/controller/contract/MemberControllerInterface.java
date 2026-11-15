package com.kitchensink.controller.contract;



import com.kitchensink.dto.common.PageResponse;
import com.kitchensink.dto.member.BulkDeleteRequest;
import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberResponse;
import com.kitchensink.dto.member.UpdateMemberRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Members")
@RequestMapping("/api/v1/members")
public interface MemberControllerInterface {

    @Operation(summary = "Search, sort and paginate members",
            description = "q is a case-insensitive prefix match on name, email or phone. sort is field,asc|desc "
                    + "with field one of name, email, createdAt.")
    @GetMapping
    PageResponse<MemberResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(defaultValue = "name,asc") String sort);

    @Operation(summary = "Get one member")
    @GetMapping("/{id}")
    MemberResponse get(@PathVariable String id);

    @Operation(summary = "Register a member")
    @PostMapping
    ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest body);

    @Operation(summary = "Replace a member's details (optimistic locking via version)")
    @PutMapping("/{id}")
    MemberResponse update(@PathVariable String id, @Valid @RequestBody UpdateMemberRequest body);

    @Operation(summary = "Delete a member")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id);

    @Operation(summary = "Delete several members; unknown ids are reported, not fatal")
    @PostMapping("/bulk-delete")
    BulkDeleteResponse bulkDelete(@Valid @RequestBody BulkDeleteRequest body);
}
