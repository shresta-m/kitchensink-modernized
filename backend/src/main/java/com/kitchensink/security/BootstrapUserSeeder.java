package com.kitchensink.security;



import com.kitchensink.config.AppProperties;
import com.kitchensink.exception.domain.DuplicateUsernameException;
import com.kitchensink.model.Role;
import com.kitchensink.service.UserService;
import com.kitchensink.validation.UserRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates first-run users from environment variables. There are no built-in default accounts:
 * if the variables are not set, nothing is created. Passwords are never logged.
 */
@Component
public class BootstrapUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUserSeeder.class);
    private final UserService users;
    private final AppProperties.Bootstrap bootstrap;

    public BootstrapUserSeeder(UserService users, AppProperties props) {
        this.users = users;
        this.bootstrap = props.bootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        seed(bootstrap.adminUsername(), bootstrap.adminPassword(), Role.ADMIN);
        seed(bootstrap.viewerUsername(), bootstrap.viewerPassword(), Role.VIEWER);
    }

    private void seed(String username, String password, Role role) {
        if (isBlank(username) && isBlank(password)) {
            return;
        }
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalStateException("Bootstrap " + role + " needs both a username and a password");
        }
        if (password.length() < UserRules.PASSWORD_MIN) {
            throw new IllegalStateException("Bootstrap " + role + " password must be at least "
                    + UserRules.PASSWORD_MIN + " characters");
        }
        if (!username.trim().matches(UserRules.USERNAME_REGEX)) {
            throw new IllegalStateException("Bootstrap " + role + " username: " + UserRules.USERNAME_MESSAGE);
        }
        try {
            users.create(username, password, role);
        } catch (DuplicateUsernameException alreadyThere) {
            log.debug("Bootstrap user already exists role={}", role);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
