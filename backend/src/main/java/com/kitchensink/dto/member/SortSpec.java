package com.kitchensink.dto.member;




import com.kitchensink.exception.domain.InvalidQueryException;
import java.util.Locale;
import java.util.Objects;

public record SortSpec(MemberSort field, SortDirection direction) {

    public static final SortSpec DEFAULT = new SortSpec(MemberSort.NAME, SortDirection.ASC);

    public SortSpec {
        Objects.requireNonNull(field);
        Objects.requireNonNull(direction);
    }

    /** Parses "field" or "field,asc|desc". Blank input yields the default. */
    public static SortSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String[] parts = raw.split(",", -1);
        if (parts.length > 2) {
            throw new InvalidQueryException("sort", "Use the form field,asc or field,desc");
        }
        MemberSort field = MemberSort.fromParam(parts[0].trim());
        SortDirection dir = parts.length == 2 ? SortDirection.fromParam(parts[1].trim()) : SortDirection.ASC;
        return new SortSpec(field, dir);
    }

    public String toParam() {
        return field.param() + "," + direction.name().toLowerCase(Locale.ROOT);
    }
}
