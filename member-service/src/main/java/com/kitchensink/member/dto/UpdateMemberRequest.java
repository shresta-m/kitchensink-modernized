package com.kitchensink.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Profile edit only. Role changes go through {@link ChangeMemberRoleRequest}. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateMemberRequest extends MemberProfileRequest {

    @Schema(example = "0", description = "The version from the member you loaded. A different value means someone else saved first.")
    @NotNull(message = "Version is required")
    private Long version;
}
