package com.kitchensink.service.impl;



import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.exception.domain.InvalidCredentialsException;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.security.JwtTokenService;
import com.kitchensink.service.AuthService;
import com.kitchensink.service.UserService;
import com.kitchensink.service.port.AdminSignupGate;
import com.kitchensink.service.port.UserStore;
import com.kitchensink.validation.UserRules;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** The only role a caller can obtain without an administrator. */
    static final Role SELF_REGISTRATION_ROLE = Role.VIEWER;

    private final UserStore users;
    private final UserService userService;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final AdminSignupGate adminGate;

    /** Compared against when the user does not exist, so response time does not reveal valid usernames. */
    private final String timingEqualizerHash;

    public AuthServiceImpl(UserStore users, UserService userService, PasswordEncoder encoder,
            JwtTokenService tokens, AdminSignupGate adminGate) {
        this.users = users;
        this.userService = userService;
        this.encoder = encoder;
        this.tokens = tokens;
        this.adminGate = adminGate;
        this.timingEqualizerHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    @Override
    public AuthToken login(String username, String rawPassword) {
        Optional<User> user = users.findByUsername(UserRules.normalizeUsername(username));
        String hash = user.map(User::getPasswordHash).orElse(timingEqualizerHash);
        boolean passwordMatches = encoder.matches(rawPassword == null ? "" : rawPassword, hash);
        if (user.isEmpty() || !user.get().isEnabled() || !passwordMatches) {
            log.warn("Login failed");
            throw new InvalidCredentialsException();
        }
        log.info("Login succeeded username={}", user.get().getUsername());
        return tokens.issue(user.get());
    }

    @Override
    public AuthToken register(String username, String rawPassword) {
        User created = userService.create(username, rawPassword, SELF_REGISTRATION_ROLE);
        return tokens.issue(created);
    }

    @Override
    public User registerAdmin(String username, String rawPassword, String adminCode) {
        adminGate.verify(adminCode); // before the username is touched: no code, no information
        User created = userService.create(username, rawPassword, Role.ADMIN);
        log.info("Admin account created through sign-up username={}", created.getUsername());
        return created;
    }
}
