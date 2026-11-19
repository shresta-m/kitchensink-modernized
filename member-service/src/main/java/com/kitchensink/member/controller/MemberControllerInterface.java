package com.kitchensink.member.controller;

import com.kitchensink.common.dto.ApiError;
import com.kitchensink.common.dto.PageResponse;
import com.kitchensink.member.dto.BulkDeleteRequest;
import com.kitchensink.member.dto.BulkDeleteResponse;
import com.kitchensink.member.dto.ChangeMemberRoleRequest;
import com.kitchensink.member.dto.CreateMemberRequest;
import com.kitchensink.member.dto.CreateMemberResponse;
import com.kitchensink.member.dto.MemberResponse;
import com.kitchensink.member.dto.ResetPasswordResponse;
import com.kitchensink.member.dto.UpdateMemberRequest;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * The members API contract and its OpenAPI documentation. {@link MemberController} implements it and keeps only the
 * routing, validation and mapping code, so the endpoints stay readable.
 */
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Members", description = "Viewers can list, search, view and export. Administrators can do everything.")
public interface MemberControllerInterface {

    String NOT_FOUND = "Member not found";
    int SEARCH_MAX = 100;
    String SEARCH_MESSAGE = "search must be at most 100 characters";

    @Operation(summary = "List members",
            description = "Search is a case-insensitive prefix match on name, email and phone. Sorted and paged on the server.")
    @ApiResponse(responseCode = "400", description = "Invalid sort, page or filter value",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    PageResponse<MemberResponse> list(
            @Parameter(description = "Starts-with match on name, email or phone")
            @Size(max = SEARCH_MAX, message = SEARCH_MESSAGE) String search,
            Role role,
            MemberStatus status,
            @Parameter(description = "field,direction. Fields: name, email, role, createdAt. Example: name,asc") String sort,
            @Parameter(description = "Zero-based page number") @Min(value = 0, message = "page must be 0 or more") int page,
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = 100, message = "size must be between 1 and 100") int size);

    @Operation(summary = "Export members as CSV",
            description = "Same filters and sort as the list, but returns every match as a file download.")
    @ApiResponse(responseCode = "200", description = "CSV file",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Invalid sort or too many rows",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<byte[]> export(@Size(max = SEARCH_MAX, message = SEARCH_MESSAGE) String search, Role role, MemberStatus status, String sort);

    @Operation(summary = "Get one member")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    MemberResponse get(String id);

    @Operation(summary = "Create a member",
            description = "Generates a temporary password and returns it once. The member must change it at first sign-in.")
    @ApiResponse(responseCode = "201", description = "Member created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<CreateMemberResponse> create(@Valid CreateMemberRequest request);

    @Operation(summary = "Approve a registration",
            description = "Lets a self-registered member sign in. To reject a registration, delete the pending member.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The member is not waiting for approval",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    MemberResponse approve(String id);

    @Operation(summary = "Reset a member's password",
            description = "Generates a new temporary password and returns it once. The old password stops working.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<ResetPasswordResponse> resetPassword(String id);

    @Operation(summary = "Update a member's profile",
            description = "Name, email, country code and phone only. Send the version you loaded; a stale version returns 409. Use PUT /{id}/role to change the role.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email already used, or the version is out of date",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    MemberResponse update(String id, @Valid UpdateMemberRequest request);

    @Operation(summary = "Change a member's role")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Would remove the last administrator, or the version is out of date",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    MemberResponse changeRole(String id, @Valid ChangeMemberRoleRequest request);

    @Operation(summary = "Delete a member")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = NOT_FOUND,
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Administrators must be demoted before they can be deleted",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    ResponseEntity<Void> delete(String id);

    @Operation(summary = "Delete several members by id",
            description = "Unknown ids are ignored; the response says how many were actually deleted.")
    @ApiResponse(responseCode = "409", description = "The selection contains an administrator. Demote them first",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    BulkDeleteResponse bulkDelete(@Valid BulkDeleteRequest request);
}
