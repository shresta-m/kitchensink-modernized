package com.kitchensink.member.validation;

import java.util.Locale;

/**
 * Single source of truth for member field rules. DTO annotations use the constants, the entity uses the normalizers,
 * so a rule changes in one place. The frontend mirrors these in {@code src/lib/validation.ts}.
 */
public final class MemberRules {

    private MemberRules() {}

    /** Trims and collapses inner whitespace: "  Asha   Rao " becomes "Asha Rao". Case is preserved. */
    public static String normalizeName(String name) {
        return required(name, "name").trim().replaceAll("\\s+", " ");
    }

    /** Trims and lowercases, so the unique index and prefix search are case-insensitive. */
    public static String normalizeEmail(String email) {
        return required(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /** Removes spaces and guarantees a leading '+': "91" and " +91 " both become "+91". */
    public static String normalizeCountryCode(String countryCode) {
        String code = required(countryCode, "countryCode").replaceAll("\\s+", "");
        return code.startsWith("+") ? code : "+" + code;
    }

    /** Keeps digits only: "98765 43210" and "98765-43210" both become "9876543210". */
    public static String normalizePhone(String phone) {
        return required(phone, "phone").replaceAll("\\D", "");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");   // domain invariant, DTO validation runs first
        }
        return value;
    }
}