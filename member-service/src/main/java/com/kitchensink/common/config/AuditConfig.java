package com.kitchensink.common.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Fills Member.createdBy / updatedBy on every save with the signed-in member's id. */
@Configuration
@EnableMongoAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    static final String SYSTEM = "system";

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(currentActor());
    }

    /** Self-registration and other anonymous calls have no signed-in member, so they are recorded as "system". */
    public static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        return authentication.getName();
    }
}
