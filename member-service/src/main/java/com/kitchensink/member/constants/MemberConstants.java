package com.kitchensink.member.constants;

public class MemberConstants {

    // ---- limits and patterns (compile-time constants, usable inside annotations) ----
    public static final int NAME_MAX = 25;
    public static final String NAME_NO_DIGITS = "^\\D+$";
    public static final String EMAIL = "^[^\\s@]+@([^\\s@.]+\\.)+[^\\s@.]{2,}$";
    public static final int EMAIL_MAX = 254;
    public static final String COUNTRY_CODE_PATTERN = "^\\+\\d{1,4}$";
    public static final String PWD_FORMAT = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$";

    // ---- messages ----
    public static final String NAME_MESSAGE = "Name must be 1 to 25 characters and must not contain digits";
    public static final String EMAIL_MESSAGE = "Enter a valid email address";
    public static final String COUNTRY_CODE_MESSAGE = "Country code must look like +91";
    public static final String PWD_MESSAGE = "Password must be 8 to 72 characters with a letter, a number and a special character";
    public static final String VALID_PHONE_NUMBER_FOR_THE_SELECTED_COUNTRY = "Enter a valid phone number for the selected country";
    public static final String COUNTRY_CODE = "countryCode";
    public static final String PHONE = "phone";

    public static final int PASSWORD_MAX = 72;
}
