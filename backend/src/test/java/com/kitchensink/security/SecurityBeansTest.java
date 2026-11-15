package com.kitchensink.security;




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

class SecurityBeansTest {

    private static AppProperties props(String secret, String issuer) {
        return new AppProperties(
                new AppProperties.Security(secret, issuer, Duration.ofMinutes(30), false,
                        List.of("http://localhost:5173"), 10, ""),
                new AppProperties.BulkImport(10, 2, 100, 10),
                new AppProperties.Bootstrap(null, null, null, null));
    }

    private static final String SECRET = "0123456789-0123456789-0123456789-abcdef";


    // ---- JWT ----------------------------------------------------------------------------------

    @Test
    void issuedTokensDecodeWithTheRoleClaimAndExpiry() {
        JwtConfig cfg = new JwtConfig(props(SECRET, "kitchensink"));
        JwtTokenService tokens = new JwtTokenService(cfg.jwtEncoder(), props(SECRET, "kitchensink"), Clock.systemUTC());

        AuthToken token = tokens.issue(new User("jane", "hash", Role.ADMIN, Instant.EPOCH));
        Jwt jwt = cfg.jwtDecoder().decode(token.accessToken());

        assertThat(token.expiresInSeconds()).isEqualTo(1800);
        assertThat(token.username()).isEqualTo("jane");
        assertThat(token.role()).isEqualTo(Role.ADMIN);
        assertThat(jwt.getSubject()).isEqualTo("jane");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("kitchensink");
    }

    @Test
    void tokensFromAnotherIssuerOrKeyAreRejected() {
        JwtConfig ours = new JwtConfig(props(SECRET, "kitchensink"));
        JwtConfig otherIssuer = new JwtConfig(props(SECRET, "someone-else"));
        JwtConfig otherKey = new JwtConfig(props("another-0123456789-0123456789-0123456789", "kitchensink"));
        User u = new User("jane", "hash", Role.VIEWER, Instant.EPOCH);

        String wrongIssuer = new JwtTokenService(otherIssuer.jwtEncoder(), props(SECRET, "someone-else"), Clock.systemUTC())
                .issue(u).accessToken();
        String wrongKey = new JwtTokenService(otherKey.jwtEncoder(), props(SECRET, "kitchensink"), Clock.systemUTC())
                .issue(u).accessToken();

        assertThatThrownBy(() -> ours.jwtDecoder().decode(wrongIssuer)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> ours.jwtDecoder().decode(wrongKey)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void expiredTokensAreRejected() {
        JwtConfig cfg = new JwtConfig(props(SECRET, "kitchensink"));
        Clock longAgo = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);
        String token = new JwtTokenService(cfg.jwtEncoder(), props(SECRET, "kitchensink"), longAgo)
                .issue(new User("jane", "hash", Role.VIEWER, Instant.EPOCH)).accessToken();

        assertThatThrownBy(() -> cfg.jwtDecoder().decode(token)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void passwordEncoderHashesWithBcryptAndVerifies() {
        var encoder = new JwtConfig(props(SECRET, "kitchensink")).passwordEncoder();
        String hash = encoder.encode("correct horse battery");

        assertThat(hash).startsWith("$2").isNotEqualTo("correct horse battery");
        assertThat(encoder.matches("correct horse battery", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void rolesClaimBecomesRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("jane")
                .claim("roles", List.of("ADMIN")).build();

        AbstractAuthenticationToken auth = SecurityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(auth.getName()).isEqualTo("jane");
    }


    // ---- CORS ---------------------------------------------------------------------------------

    @Test
    void corsAllowsOnlyConfiguredOriginsWithoutCredentials() {
        var source = new CorsConfig(props(SECRET, "kitchensink")).corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/members"));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getExposedHeaders()).contains("Content-Disposition", "X-Request-Id");
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }

}
