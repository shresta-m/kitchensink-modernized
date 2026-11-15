package com.kitchensink.config;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.security.CorsConfig;
import com.kitchensink.security.JwtConfig;
import com.kitchensink.security.JwtTokenService;
import com.kitchensink.security.SecurityConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.cors.CorsConfiguration;

class ConfigTest {

    private static AppProperties props(String secret, String issuer) {
        return new AppProperties(
                new AppProperties.Security(secret, issuer, Duration.ofMinutes(30), false,
                        List.of("http://localhost:5173"), 10, ""),
                new AppProperties.BulkImport(10, 2, 100, 10),
                new AppProperties.Bootstrap(null, null, null, null));
    }

    private static final String SECRET = "0123456789-0123456789-0123456789-abcdef";


    // ---- AppConfig ----------------------------------------------------------------------------

    @Test
    void appConfigBeans() {
        AppConfig cfg = new AppConfig();
        assertThat(cfg.clock().getZone()).isEqualTo(java.time.ZoneOffset.UTC);

        ThreadPoolTaskExecutor executor = cfg.importExecutor(props(SECRET, "kitchensink"));
        executor.initialize();
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("import-");

            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            cfg.importExecutorMetrics(executor).bindTo(registry);
            assertThat(registry.get("members.import.pool.active").gauge().value()).isZero();
            assertThat(registry.get("members.import.pool.size").gauge().value()).isGreaterThanOrEqualTo(0);
        } finally {
            executor.shutdown();
        }

        OpenAPI api = cfg.openApi();
        assertThat(api.getInfo().getTitle()).isEqualTo("Kitchensink Members API");
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }


    // ---- AppProperties ------------------------------------------------------------------------

    @Test
    void adminSignupIsEnabledOnlyWithANonBlankCode() {
        assertThat(security("").adminSignupEnabled()).isFalse();
        assertThat(security("   ").adminSignupEnabled()).isFalse();
        assertThat(security(null).adminSignupEnabled()).isFalse();
        assertThat(security("a-16-char-secret!").adminSignupEnabled()).isTrue();
    }

    private static AppProperties.Security security(String code) {
        return new AppProperties.Security(SECRET, "k", Duration.ofMinutes(1), false, List.of("http://x"), 10, code);
    }
}
