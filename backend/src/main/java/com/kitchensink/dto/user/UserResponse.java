package com.kitchensink.dto.user;



import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import java.time.Instant;

/** Never carries the password hash. */
public record UserResponse(String id, String username, Role role, boolean enabled, Instant createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
