package com.kitchensink.service.impl;



import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.service.UserService;
import com.kitchensink.service.port.UserStore;
import com.kitchensink.validation.UserRules;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserStore users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UserServiceImpl(UserStore users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    public User create(String username, String rawPassword, Role role) {
        String normalized = UserRules.normalizeUsername(username);
        User user = new User(normalized, encoder.encode(rawPassword), role, clock.instant());
        user = users.insert(user); // the unique index (translated by the store) arbitrates concurrent sign-ups
        log.info("User created username={} role={}", normalized, role);
        return user;
    }

    @Override
    public List<User> list() {
        return users.findAllOrderedByUsername();
    }
}
