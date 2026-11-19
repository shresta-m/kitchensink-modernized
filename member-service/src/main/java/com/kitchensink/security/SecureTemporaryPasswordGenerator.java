package com.kitchensink.security;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 12 random characters from a cryptographically secure source. Look-alike characters (0/O, 1/l/I) are left out so the
 * password survives being read aloud or typed from a screen, and it always contains a letter, a digit and a special
 * character so it passes the same password rule the member must follow when choosing their own (MemberRules).
 */
@Component
public class SecureTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "@#$%&*!?";
    private static final String ALL = LETTERS + DIGITS + SPECIALS;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(LETTERS));
        chars.add(pick(DIGITS));
        chars.add(pick(SPECIALS));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);        // so the guaranteed characters are not always first
        StringBuilder password = new StringBuilder(LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
