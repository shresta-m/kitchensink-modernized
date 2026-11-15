package com.kitchensink.dto.member;




import com.kitchensink.exception.domain.InvalidQueryException;
import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection fromParam(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new InvalidQueryException("sort", "Sort direction must be asc or desc");
        };
    }
}
