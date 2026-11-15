package com.kitchensink.service;



import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.model.User;

public interface AuthService {

    /** @throws com.kitchensink.exception.domain.InvalidCredentialsException for any failure, without saying which part */
    AuthToken login(String username, String rawPassword);

    /**
     * Public sign-up. The role is fixed by the server (always VIEWER) and cannot be requested by the caller.
     *
     * @throws com.kitchensink.exception.domain.DuplicateUsernameException if the username is taken
     */
    AuthToken register(String username, String rawPassword);

    /**
     * Creates an ADMIN, but only when the caller presents the configured sign-up code. The code is checked
     * before the username is touched, so a caller without it learns nothing about existing accounts. Does not
     * sign the new admin in.
     *
     * @throws com.kitchensink.exception.domain.AdminSignupDisabledException if no code is configured
     * @throws com.kitchensink.exception.domain.InvalidAdminCodeException if the code is wrong
     * @throws com.kitchensink.exception.domain.DuplicateUsernameException if the username is taken
     */
    User registerAdmin(String username, String rawPassword, String adminCode);
}
