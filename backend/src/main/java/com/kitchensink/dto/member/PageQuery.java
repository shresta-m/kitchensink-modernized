package com.kitchensink.dto.member;




import com.kitchensink.exception.domain.InvalidQueryException;
import java.util.Objects;

public record PageQuery(int page, int size, SortSpec sort) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new InvalidQueryException("page", "Must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidQueryException("size", "Must be between 1 and " + MAX_SIZE);
        }
        Objects.requireNonNull(sort);
    }

    public long offset() {
        return (long) page * size;
    }
}
