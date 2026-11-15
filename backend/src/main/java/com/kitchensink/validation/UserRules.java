package com.kitchensink.validation;



import java.util.Locale;

/** Single source of truth for account rules, shared by sign-up, admin user creation and bootstrap seeding. */
public final class UserRules {

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 50;
    /** Letters, digits, dot, underscore, hyphen. Stored lowercase, so "Admin" and "admin" are one account. */
    public static final String USERNAME_REGEX = "[A-Za-z0-9._-]{3,50}";
    public static final String USERNAME_MESSAGE =
            "Username must be 3 to 50 characters: letters, digits, dot, underscore or hyphen";
    public static final int PASSWORD_MIN = 12;
    /** BCrypt only uses the first 72 bytes; the cap also stops oversized request bodies being hashed. */
    public static final int PASSWORD_MAX = 128;

    private UserRules() {}

    public static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
