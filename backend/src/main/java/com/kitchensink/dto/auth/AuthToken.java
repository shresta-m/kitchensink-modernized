package com.kitchensink.dto.auth;



import com.kitchensink.model.Role;

/** Result of a successful login, before it is shaped into the HTTP response. */
public record AuthToken(String accessToken, long expiresInSeconds, String username, Role role) {}
