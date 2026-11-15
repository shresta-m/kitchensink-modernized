package com.kitchensink.dto.auth;


public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserInfo user) {

    public record UserInfo(String username, String role) {}

    public static LoginResponse from(AuthToken t) {
        return new LoginResponse(t.accessToken(), "Bearer", t.expiresInSeconds(),
                new UserInfo(t.username(), t.role().name()));
    }
}
