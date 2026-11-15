package com.kitchensink.dto.member;




import com.kitchensink.exception.domain.InvalidQueryException;
import java.util.Arrays;

/** Whitelist of sortable fields; anything else is rejected, never passed to the database. */
public enum MemberSort {
    NAME("name"),
    EMAIL("email"),
    CREATED_AT("createdAt");

    private final String param;

    MemberSort(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static MemberSort fromParam(String raw) {
        return Arrays.stream(values())
                .filter(s -> s.param.equals(raw))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryException("sort", "Sort field must be one of: name, email, createdAt"));
    }
}
