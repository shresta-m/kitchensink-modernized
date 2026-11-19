package com.kitchensink.lookup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** One dropdown entry: send {@code value} back to the API, show {@code label} to people. */
@Getter
@Builder
public class LookupOption {
    @Schema(example = "ADMIN")
    private final String value;

    @Schema(example = "Admin")
    private final String label;
}
