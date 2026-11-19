package com.kitchensink.member.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

public final class PhoneRules {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneRules() {}

    /** Real number for the given country code, and not an obvious dummy. */
    public static boolean isValid(String countryCode, String phone) {
        if (countryCode == null || phone == null) {
            return false;
        }
        String digits = MemberRules.normalizePhone(phone);
        if (isDummy(digits)) {
            return false;
        }
        try {
            PhoneNumber parsed = UTIL.parse(MemberRules.normalizeCountryCode(countryCode) + digits, null);
            return UTIL.isValidNumber(parsed);
        } catch (NumberParseException e) {
            return false;   // unknown country code, too short, not a number
        }
    }

    /** One repeated digit, or a straight ascending/descending run (wrapping 9 to 0). */
    static boolean isDummy(String digits) {
        boolean same = true;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < digits.length(); i++) {
            int step = (digits.charAt(i) - digits.charAt(i - 1) + 10) % 10;
            same &= step == 0;
            ascending &= step == 1;
            descending &= step == 9;
        }
        return same || ascending || descending;
    }
}