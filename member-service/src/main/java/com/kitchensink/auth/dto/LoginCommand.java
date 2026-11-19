package com.kitchensink.auth.dto;

/** {@code clientIp} is the address the server saw; it feeds the per-address lockout and the API log line. */
public record LoginCommand(String email, String password, String clientIp) {
}
