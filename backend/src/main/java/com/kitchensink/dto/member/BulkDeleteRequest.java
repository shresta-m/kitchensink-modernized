package com.kitchensink.dto.member;



import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkDeleteRequest(
        @NotEmpty(message = "At least one id is required")
        @Size(max = 100, message = "At most 100 ids per request")
        List<@NotBlank(message = "Ids must not be blank") String> ids) {}
