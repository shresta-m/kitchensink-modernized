package com.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.TestData;
import com.kitchensink.common.config.AppProperties;
import com.kitchensink.member.model.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtTokenServiceTest {

    private final AppProperties props = TestData.props("CODE");
    private final SecurityConfig config = new SecurityConfig();
    private final JwtDecoder decoder = config.jwtDecoder(props);

    private JwtTokenService service(Instant at) {
        return new JwtTokenService(config.jwtEncoder(props), props, Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void tokenCarriesOnlyIdIssuerAndThirtyMinuteWindow() {
        Instant now = Instant.now();

        JwtTokenService.IssuedToken issued = service(now).issue(TestData.member("42", Role.ADMIN, "a@example.com"));

        assertThat(issued.expiresInSeconds()).isEqualTo(1800);
        Jwt jwt = decoder.decode(issued.value());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(SecurityConfig.ISSUER);
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(1800);
        assertThat(jwt.getClaims()).doesNotContainKeys("role", "email", "scope");     // role is read live from the database
    }

    @Test
    void tokenCarriesTheMembersCurrentSessionGeneration() {
        var member = TestData.member("42", Role.VIEWER, "a@example.com");
        member.revokeSessions(TestData.NOW);
        member.revokeSessions(TestData.NOW);

        Jwt jwt = decoder.decode(service(Instant.now()).issue(member).value());

        Number ver = jwt.getClaim(JwtTokenService.TOKEN_VERSION_CLAIM);
        assertThat(ver.intValue()).isEqualTo(2);
    }
}
