package com.kitchensink.security;

/** Produces the one-time password an administrator hands to a member. */
public interface TemporaryPasswordGenerator {
    String generate();
}
