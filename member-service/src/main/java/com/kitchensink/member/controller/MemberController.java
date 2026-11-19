package com.kitchensink.member.controller;

import com.kitchensink.common.dto.PageResponse;
import com.kitchensink.member.dto.BulkDeleteRequest;
import com.kitchensink.member.dto.BulkDeleteResponse;
import com.kitchensink.member.dto.ChangeMemberRoleRequest;
import com.kitchensink.member.dto.CreateMemberRequest;
import com.kitchensink.member.dto.CreateMemberResponse;
import com.kitchensink.member.dto.MemberResponse;
import com.kitchensink.member.dto.ResetPasswordResponse;
import com.kitchensink.member.dto.UpdateMemberRequest;
import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.service.ChangeRoleCommand;
import com.kitchensink.member.service.CreateMemberCommand;
import com.kitchensink.member.service.CreatedMember;
import com.kitchensink.member.service.CsvExport;
import com.kitchensink.member.service.MemberService;
import com.kitchensink.member.service.UpdateMemberCommand;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Routing, validation and DTO mapping for members. The use cases live in {@link MemberService}. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerInterface {

    private final MemberService memberService;

    @Override
    @GetMapping
    public PageResponse<MemberResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(defaultValue = "name,asc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return PageResponse.of(
                memberService.list(new MemberSearchCriteria(search, role, status), sort, page, size),
                MemberResponse::from);
    }

    @Override
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) MemberStatus status,
            @RequestParam(defaultValue = "name,asc") String sort) {
        CsvExport file = memberService.exportCsv(new MemberSearchCriteria(search, role, status), sort);
        // Content type is set explicitly (not via "produces") so error responses can still be JSON.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.content());
    }

    @Override
    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable String id) {
        return MemberResponse.from(memberService.get(id));
    }

    @Override
    @PostMapping
    public ResponseEntity<CreateMemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
        CreatedMember created = memberService.create(new CreateMemberCommand(request.toDetails(), request.getRole()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())           // the response contains a password
                .body(CreateMemberResponse.builder()
                        .member(MemberResponse.from(created.member()))
                        .temporaryPassword(created.temporaryPassword())
                        .build());
    }

    @Override
    @PostMapping("/{id}/approve")
    public MemberResponse approve(@PathVariable String id) {
        return MemberResponse.from(memberService.approve(id));
    }

    @Override
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable String id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ResetPasswordResponse.builder().temporaryPassword(memberService.resetPassword(id)).build());
    }

    @Override
    @PutMapping("/{id}")
    public MemberResponse update(@PathVariable String id, @Valid @RequestBody UpdateMemberRequest request) {
        return MemberResponse.from(
                memberService.update(id, new UpdateMemberCommand(request.toDetails(), request.getVersion())));
    }

    @Override
    @PutMapping("/{id}/role")
    public MemberResponse changeRole(@PathVariable String id, @Valid @RequestBody ChangeMemberRoleRequest request) {
        return MemberResponse.from(
                memberService.changeRole(id, new ChangeRoleCommand(request.getRole(), request.getVersion())));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        memberService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/bulk-delete")
    public BulkDeleteResponse bulkDelete(@Valid @RequestBody BulkDeleteRequest request) {
        return BulkDeleteResponse.builder().deletedCount(memberService.bulkDelete(request.getIds())).build();
    }
}
