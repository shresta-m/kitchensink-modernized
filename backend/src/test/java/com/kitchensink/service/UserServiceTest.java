package com.kitchensink.service;




import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.service.impl.UserServiceImpl;
import com.kitchensink.service.port.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserStore repository;

    private final Instant now = Instant.parse("2026-11-14T10:00:00Z");
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // A reversible fake keeps the test fast and lets us assert the raw password is never stored.
        PasswordEncoder encoder = new PasswordEncoder() {
            @Override public String encode(CharSequence raw) { return "hash:" + raw; }
            @Override public boolean matches(CharSequence raw, String enc) { return enc.equals("hash:" + raw); }
        };
        service = new UserServiceImpl(repository, encoder, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesUsernameHashesPasswordAndStampsCreationTime() {
        when(repository.insert(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = service.create("  Jane.Doe ", "a-long-password-1", Role.ADMIN);

        assertThat(created.getUsername()).isEqualTo("jane.doe");
        assertThat(created.getPasswordHash()).isEqualTo("hash:a-long-password-1").isNotEqualTo("a-long-password-1");
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getCreatedAt()).isEqualTo(now);
    }
}
