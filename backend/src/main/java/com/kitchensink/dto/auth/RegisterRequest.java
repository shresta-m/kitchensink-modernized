package com.kitchensink.dto.auth;



import com.kitchensink.validation.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public sign-up. There is deliberately no role field: self-registered accounts are always viewers. */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = UserRules.USERNAME_REGEX, message = UserRules.USERNAME_MESSAGE) String username,
        @NotBlank(message = "Password is required")
        @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 12 to 128 characters") String password) {}
