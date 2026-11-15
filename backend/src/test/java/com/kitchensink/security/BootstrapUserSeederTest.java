package com.kitchensink.security;




import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.config.AppProperties;
import com.kitchensink.exception.domain.DuplicateUsernameException;
import com.kitchensink.model.Role;
import com.kitchensink.service.UserService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BootstrapUserSeederTest {

    private final UserService users = mock(UserService.class);

    private BootstrapUserSeeder seeder(String au, String ap, String vu, String vp) {
        AppProperties props = new AppProperties(
                new AppProperties.Security("x".repeat(32), "test", Duration.ofMinutes(5), false,
                        List.of("http://localhost"), 10, ""),
                new AppProperties.BulkImport(10, 1, 100, 10),
                new AppProperties.Bootstrap(au, ap, vu, vp));
        return new BootstrapUserSeeder(users, props);
    }

    @Test
    void nothingIsCreatedWhenNothingIsConfigured() {
        seeder(null, null, "", " ").run(null);

        verify(users, never()).create(any(), any(), any());
    }

    @Test
    void configuredAccountsAreCreatedWithTheirRoles() {
        seeder("admin", "a-long-password-1", "viewer", "b-long-password-2").run(null);

        verify(users).create("admin", "a-long-password-1", Role.ADMIN);
        verify(users).create("viewer", "b-long-password-2", Role.VIEWER);
    }

    @Test
    void halfConfiguredAccountsFailStartup() {
        assertThatThrownBy(() -> seeder("admin", null, null, null).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("needs both");
        assertThatThrownBy(() -> seeder(null, "a-long-password-1", null, null).run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortPasswordsAndBadUsernamesFailStartup() {
        assertThatThrownBy(() -> seeder("admin", "short", null, null).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least");
        assertThatThrownBy(() -> seeder("a b", "a-long-password-1", null, null).run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("username");
    }

    @Test
    void existingAccountsAreLeftAlone() {
        when(users.create("admin", "a-long-password-1", Role.ADMIN)).thenThrow(new DuplicateUsernameException(null));

        assertThatCode(() -> seeder("admin", "a-long-password-1", null, null).run(null)).doesNotThrowAnyException();
    }
}
