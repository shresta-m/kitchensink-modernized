package com.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class MemberJwtAuthenticationConverterTest {

    private final MemberRepository repository = mock(MemberRepository.class);
    private final MemberJwtAuthenticationConverter converter = new MemberJwtAuthenticationConverter(repository);

    private static Jwt jwt(String subject) {
        return jwt(subject, 0);
    }

    private static Jwt jwt(String subject, Number version) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject).claim("ver", version)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void roleComesFromTheCurrentMember() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.ADMIN, "a@example.com")));

        AbstractAuthenticationToken auth = converter.convert(jwt("1"));

        assertThat(auth.getName()).isEqualTo("1");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void temporaryPasswordGrantsNoRole() {
        Member temp = Member.createByAdmin(TestData.details("a@example.com"), Role.ADMIN, "h", TestData.NOW);
        org.springframework.test.util.ReflectionTestUtils.setField(temp, "id", "2");
        when(repository.findById("2")).thenReturn(Optional.of(temp));

        AbstractAuthenticationToken auth = converter.convert(jwt("2"));

        assertThat(auth.getAuthorities()).extracting(Object::toString)
                .containsExactly("PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void deletedMemberIsRejected() {
        when(repository.findById("9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt("9"))).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void aTokenFromBeforeASignOutOrPasswordChangeIsRejected() {
        Member member = TestData.member("1", Role.ADMIN, "a@example.com");
        member.revokeSessions(TestData.NOW);                       // tokenVersion is now 1
        when(repository.findById("1")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> converter.convert(jwt("1", 0)))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Session is no longer valid");
        assertThat(converter.convert(jwt("1", 1L)).isAuthenticated()).isTrue();     // the new generation works
    }

    @Test
    void aTokenWithoutAVersionClaimIsRejected() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.ADMIN, "a@example.com")));
        Jwt noVersion = Jwt.withTokenValue("t").header("alg", "HS256").subject("1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThatThrownBy(() -> converter.convert(noVersion)).isInstanceOf(BadCredentialsException.class);
    }
}
