package com.kitchensink.auth.dto;

import com.kitchensink.auth.validation.ValidPassword;
import com.kitchensink.common.validation.FieldsMatch;
import com.kitchensink.member.dto.MemberProfileRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@FieldsMatch(field = "password", confirmField = "confirmPassword", message = "Passwords do not match")
public class RegisterRequest extends MemberProfileRequest {

    @Schema(example = "Secret@123")
    @ValidPassword
    private String password;

    @Schema(example = "Secret@123")
    @NotBlank(message = "Confirm your password")
    private String confirmPassword;

    @Schema(nullable = true, example = "ABCD-1234-WXYZ",
            description = "Optional. Empty or missing registers a VIEWER. A valid code registers an ADMIN. A wrong code is rejected.")
    private String adminCode;

    /** Never print passwords. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + getEmail() + ", password=***, confirmPassword=***]";
    }
}
