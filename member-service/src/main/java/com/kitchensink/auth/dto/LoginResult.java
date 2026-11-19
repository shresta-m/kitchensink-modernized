package com.kitchensink.auth.dto;

import com.kitchensink.member.model.Member;

/** A successful sign-in: the member and the access token with its lifetime in seconds. */
public record LoginResult(Member member, String accessToken, long expiresInSeconds) {
}
