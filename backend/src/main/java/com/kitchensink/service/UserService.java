package com.kitchensink.service;



import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import java.util.List;

/** Account management. The single place where users are created, whoever asks (sign-up, admin, bootstrap). */
public interface UserService {

    /**
     * Creates an enabled account with a hashed password. The username is normalized (trimmed, lowercased).
     * Callers validate the password policy first; this method does not re-check it.
     *
     * @throws com.kitchensink.exception.domain.DuplicateUsernameException if the username is taken
     */
    User create(String username, String rawPassword, Role role);

    List<User> list();
}
