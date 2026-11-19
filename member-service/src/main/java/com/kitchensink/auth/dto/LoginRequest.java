package com.kitchensink.auth.dto;

import com.kitchensink.member.validation.MemberRules;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Checks that both fields are present and not absurdly large. The limits are the longest values the system can hold
 * (254 for an email, 72 for a password, which is what BCrypt reads), so they reveal no rule a valid user does not already
 * know. Format rules are not repeated here, so a login attempt reveals nothing.
 */
@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    @Schema(example = "asha@example.com")
    @NotBlank(message = "Enter your email")
    @Size(max = MemberRules.EMAIL_MAX, message = "Email is too long")
    private String email;

    @Schema(example = "Secret@123")
    @NotBlank(message = "Enter your password")
    @Size(max = 72, message = "Password is too long")
    private String password;

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
