package com.kitchensink.security;

import com.kitchensink.common.config.AppProperties;
import com.kitchensink.member.model.Member;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues access tokens. A token carries only the member id (subject), issuer, issue / expiry times and the session
 * generation ({@code ver}). The role is deliberately not in the token: it is read from the database on every request,
 * so a demotion or deletion applies at once, and a sign-out or password change invalidates the token by bumping {@code ver}.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenService {

    /** Claim holding the member's session generation; the converter compares it with the database on every request. */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final Clock clock;

    public IssuedToken issue(Member member) {
        Instant now = clock.instant();
        Duration ttl = properties.jwt().ttl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER)
                .subject(member.getId())
                .claim(TOKEN_VERSION_CLAIM, member.getTokenVersion())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(value, ttl.toSeconds());
    }

    /** A signed token and how many seconds it stays valid. */
    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
