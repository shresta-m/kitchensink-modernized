package com.kitchensink.security;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a verified token into an authentication using the member's CURRENT state: a deleted member is rejected,
 * a token from before a sign-out or password change is rejected (its {@code ver} no longer matches), and a demoted
 * member loses access immediately. A member who still has a temporary password gets no role at all,
 * only PASSWORD_CHANGE_REQUIRED, so the only things they can reach are /auth/me and /auth/change-password.
 */
@Component
@RequiredArgsConstructor
public class MemberJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";

    private final MemberRepository memberRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Member member = memberRepository.findById(jwt.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Member no longer exists"));
        Number issuedUnder = jwt.getClaim(JwtTokenService.TOKEN_VERSION_CLAIM);
        if (issuedUnder == null || issuedUnder.longValue() != member.getTokenVersion()) {
            throw new BadCredentialsException("Session is no longer valid");   // signed out, or the password changed
        }
        List<GrantedAuthority> authorities = member.isMustChangePassword()
                ? List.of(new SimpleGrantedAuthority(PASSWORD_CHANGE_REQUIRED))
                : List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, member.getId());
    }
}
