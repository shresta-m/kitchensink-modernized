package com.kitchensink.auth.dto;

import com.kitchensink.member.dto.MemberResponse;
import com.kitchensink.member.model.Member;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;                 // seconds
    /** True while the member still has a temporary password: only change-password and /me work until it is changed. */
    private final boolean mustChangePassword;
    private final MemberResponse member;

    public static LoginResponse of(Member member, String accessToken, long expiresInSeconds) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresInSeconds)
                .mustChangePassword(member.isMustChangePassword())
                .member(MemberResponse.from(member))
                .build();
    }

    @Override
    public String toString() {
        return "LoginResponse[member=" + member + ", accessToken=***]";
    }
}
