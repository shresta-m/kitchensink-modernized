package com.kitchensink.validation;



import java.util.Locale;

/** Single source of truth for member field rules and normalization. */
public final class MemberRules {

    public static final int NAME_MAX = 25;
    public static final String NAME_NO_DIGITS = "[^0-9]*";
    public static final int EMAIL_MAX = 254;
    /** Stricter than the default @Email: requires a dot in the domain part (rejects "a@b"). */
    public static final String EMAIL_REGEX = ".+@.+\\..+";
    public static final String PHONE_DIGITS = "\\d{10,12}";

    private MemberRules() {}

    public static String normalizeName(String raw) {
        return raw.trim();
    }

    public static String normalizeEmail(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePhone(String raw) {
        return raw.trim();
    }
}
