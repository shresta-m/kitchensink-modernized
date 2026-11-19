package com.kitchensink.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditConfigTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationIsRecordedAsSystem() {
        SecurityContextHolder.clearContext();

        assertThat(new AuditConfig().auditorAware().getCurrentAuditor()).contains("system");
    }

    @Test
    void unauthenticatedAndAnonymousAreRecordedAsSystem() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "pw"));
        assertThat(AuditConfig.currentActor()).isEqualTo("system");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(AuditConfig.currentActor()).isEqualTo("system");
    }

    @Test
    void signedInMemberIsRecordedById() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("member-42", "pw", "ROLE_ADMIN"));

        assertThat(new AuditConfig().auditorAware().getCurrentAuditor()).contains("member-42");
    }
}
