package com.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.TestData;
import com.kitchensink.common.config.AppProperties;
import com.kitchensink.member.model.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();
    private final AppProperties props = TestData.props("CODE");

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "short-secret", "0123456789012345678901234567890"})     // 31 bytes
    void refusesMissingOrWeakSecrets(String secret) {
        assertThatThrownBy(() -> SecurityConfig.signingKey(secret))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void acceptsA32ByteSecret() {
        assertThat(SecurityConfig.signingKey("01234567890123456789012345678901").getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void corsAllowsOnlyTheConfiguredOriginsAndExposesTheFileName() {
        CorsConfiguration cors = config.corsConfigurationSource(props).getCorsConfigurations().get("/**");

        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:5173");
        assertThat(cors.getAllowedMethods()).contains("GET", "PUT", "DELETE").doesNotContain("PATCH");
        assertThat(cors.getExposedHeaders()).contains("Content-Disposition", "X-Trace-Id", "Retry-After");
        assertThat(cors.getAllowedHeaders()).contains("traceparent", "tracestate");
    }

    private String token(JwtEncoder encoder, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void decoderAcceptsAValidToken() {
        Instant now = Instant.now();
        String value = token(config.jwtEncoder(props), SecurityConfig.ISSUER, now, now.plusSeconds(60));

        assertThat(config.jwtDecoder(props).decode(value).getSubject()).isEqualTo("1");
    }

    @Test
    void decoderRejectsExpiredTokensWithNoGracePeriod() {
        Instant now = Instant.now();
        String value = token(config.jwtEncoder(props), SecurityConfig.ISSUER, now.minusSeconds(120), now.minusSeconds(5));

        assertThatThrownBy(() -> config.jwtDecoder(props).decode(value)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsOtherIssuers() {
        Instant now = Instant.now();
        String value = token(config.jwtEncoder(props), "https://other.example", now, now.plusSeconds(60));

        assertThatThrownBy(() -> config.jwtDecoder(props).decode(value)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsTokensSignedWithAnotherSecret() {
        AppProperties other = new AppProperties("CODE",
                new AppProperties.JwtSettings("another-secret-another-secret-another-1", Duration.ofMinutes(30)), List.of());
        JwtTokenService foreign = new JwtTokenService(config.jwtEncoder(other), other, Clock.fixed(Instant.now(), ZoneOffset.UTC));
        String value = foreign.issue(TestData.member("1", Role.ADMIN, "a@example.com")).value();

        assertThatThrownBy(() -> config.jwtDecoder(props).decode(value)).isInstanceOf(JwtException.class);
    }
}
