package com.kitchensink.dto.auth;



import com.kitchensink.validation.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin sign-up. The role is not a field: this endpoint only ever creates ADMIN, and only with the secret code. */
public record AdminRegisterRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = UserRules.USERNAME_REGEX, message = UserRules.USERNAME_MESSAGE) String username,
        @NotBlank(message = "Password is required")
        @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 12 to 128 characters") String password,
        @NotBlank(message = "Admin sign-up code is required")
        @Size(max = 200, message = "Admin sign-up code is too long") String adminCode) {}
