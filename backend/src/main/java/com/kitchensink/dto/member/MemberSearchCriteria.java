package com.kitchensink.dto.member;




import com.kitchensink.exception.domain.InvalidQueryException;
import java.util.Locale;

/** Free-text prefix search across name, email and phone. */
public record MemberSearchCriteria(String text) {

    public static final int MAX_LENGTH = 100;

    public static MemberSearchCriteria of(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() > MAX_LENGTH) {
            throw new InvalidQueryException("q", "Must be at most " + MAX_LENGTH + " characters");
        }
        return new MemberSearchCriteria(t);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public String normalizedText() {
        return text.toLowerCase(Locale.ROOT);
    }
}
