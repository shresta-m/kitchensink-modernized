package com.kitchensink.security;



import com.kitchensink.config.AppProperties;
import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.model.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final AppProperties.Security security;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
        this.encoder = encoder;
        this.security = props.security();
        this.clock = clock;
    }

    public AuthToken issue(User user) {
        Instant now = clock.instant();
        Duration ttl = security.jwtTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(security.jwtIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthToken(token, ttl.toSeconds(), user.getUsername(), user.getRole());
    }
}
