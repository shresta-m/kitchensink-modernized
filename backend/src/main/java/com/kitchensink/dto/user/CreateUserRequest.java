package com.kitchensink.dto.user;



import com.kitchensink.model.Role;
import com.kitchensink.validation.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin-only account creation, where the role is chosen by the administrator. */
public record CreateUserRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = UserRules.USERNAME_REGEX, message = UserRules.USERNAME_MESSAGE) String username,
        @NotBlank(message = "Password is required")
        @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 12 to 128 characters") String password,
        @NotNull(message = "Role is required") Role role) {}
