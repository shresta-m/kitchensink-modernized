package com.kitchensink.member.dto;

import com.kitchensink.member.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeMemberRoleRequest {

    @Schema(example = "ADMIN")
    @NotNull(message = "Choose a role")
    private Role role;

    @Schema(example = "0", description = "The version from the member you loaded. A different value means someone else saved first.")
    @NotNull(message = "Version is required")
    private Long version;
}
