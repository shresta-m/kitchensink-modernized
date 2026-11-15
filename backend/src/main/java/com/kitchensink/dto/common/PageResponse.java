package com.kitchensink.dto.common;



import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, String sort) {

    public static <S, T> PageResponse<T> from(PageResult<S> page, Function<S, T> mapper, String sort) {
        return new PageResponse<>(page.content().stream().map(mapper).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages(), sort);
    }
}
