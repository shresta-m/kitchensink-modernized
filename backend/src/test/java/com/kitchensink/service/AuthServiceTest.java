package com.kitchensink.service;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.config.AppProperties;
import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.exception.domain.AdminSignupDisabledException;
import com.kitchensink.exception.domain.InvalidAdminCodeException;
import com.kitchensink.exception.domain.InvalidCredentialsException;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.security.JwtTokenService;
import com.kitchensink.service.impl.AuthServiceImpl;
import com.kitchensink.service.impl.ConfiguredAdminSignupGate;
import com.kitchensink.service.port.UserStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserStore users = mock(UserStore.class);
    private final UserService userService = mock(UserService.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override public String encode(CharSequence raw) { return "hash:" + raw; }
        @Override public boolean matches(CharSequence raw, String enc) { return enc.equals("hash:" + raw); }
    };
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = serviceWithCode("a-secret-signup-code");
    }

    private AuthServiceImpl serviceWithCode(String code) {
        AppProperties props = new AppProperties(
                new AppProperties.Security("x".repeat(32), "test", Duration.ofMinutes(5), false,
                        List.of("http://localhost"), 10, code),
                new AppProperties.BulkImport(10, 1, 100, 10),
                new AppProperties.Bootstrap(null, null, null, null));
        return new AuthServiceImpl(users, userService, encoder, tokens, new ConfiguredAdminSignupGate(props));
    }

    private static User user(String name, Role role) {
        return new User(name, "hash:right-password-1", role, Instant.EPOCH);
    }

    @Test
    void registerAlwaysCreatesAViewerAndSignsThemIn() {
        User created = user("newbie", Role.VIEWER);
        AuthToken token = new AuthToken("jwt", 1800, "newbie", Role.VIEWER);
        when(userService.create("newbie", "right-password-1", Role.VIEWER)).thenReturn(created);
        when(tokens.issue(created)).thenReturn(token);

        assertThat(service.register("newbie", "right-password-1")).isEqualTo(token);
        verify(userService, never()).create(any(), any(), eq(Role.ADMIN));
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() {
        User admin = user("admin", Role.ADMIN);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(tokens.issue(admin)).thenReturn(new AuthToken("jwt", 1800, "admin", Role.ADMIN));

        assertThat(service.login("  ADMIN ", "right-password-1").role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void wrongPasswordAndUnknownUserFailTheSameWay() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(user("admin", Role.ADMIN)));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("admin", "wrong-password-1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("ghost", "right-password-1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void registerAdminWithTheRightCodeCreatesAnAdminAndIssuesNoToken() {
        User created = user("boss", Role.ADMIN);
        when(userService.create("boss", "right-password-1", Role.ADMIN)).thenReturn(created);

        assertThat(service.registerAdmin("boss", "right-password-1", "a-secret-signup-code")).isSameAs(created);
        verify(tokens, never()).issue(any());
    }

    @Test
    void registerAdminWithAWrongCodeCreatesNothingAndNeverTouchesTheUsername() {
        assertThatThrownBy(() -> service.registerAdmin("boss", "right-password-1", "guess"))
                .isInstanceOf(InvalidAdminCodeException.class);
        assertThatThrownBy(() -> service.registerAdmin("boss", "right-password-1", null))
                .isInstanceOf(InvalidAdminCodeException.class);
        verify(userService, never()).create(any(), any(), any());
    }

    @Test
    void registerAdminIsDisabledWhenNoCodeIsConfigured() {
        assertThatThrownBy(() -> serviceWithCode("").registerAdmin("boss", "right-password-1", ""))
                .isInstanceOf(AdminSignupDisabledException.class);
        assertThatThrownBy(() -> serviceWithCode(null).registerAdmin("boss", "right-password-1", "x"))
                .isInstanceOf(AdminSignupDisabledException.class);
        verify(userService, never()).create(any(), any(), any());
    }
}
