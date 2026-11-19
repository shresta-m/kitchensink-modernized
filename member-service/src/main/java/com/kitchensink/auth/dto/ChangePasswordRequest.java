package com.kitchensink.auth.dto;

import com.kitchensink.auth.validation.ValidPassword;
import com.kitchensink.common.validation.FieldsMatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@FieldsMatch(field = "newPassword", confirmField = "confirmPassword", message = "Passwords do not match")
public class ChangePasswordRequest {

    @Schema(example = "Temp1234abcd", description = "The current password, or the temporary one an administrator gave you")
    @NotBlank(message = "Enter your current password")
    private String currentPassword;

    @Schema(example = "Secret@123")
    @ValidPassword
    private String newPassword;

    @Schema(example = "Secret@123")
    @NotBlank(message = "Confirm your new password")
    private String confirmPassword;

    @Override
    public String toString() {
        return "ChangePasswordRequest[passwords=***]";
    }
}
