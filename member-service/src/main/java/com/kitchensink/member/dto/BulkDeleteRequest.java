package com.kitchensink.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BulkDeleteRequest {

    @NotEmpty(message = "Select at least one member")
    @Size(max = 100, message = "You can delete at most 100 members at a time")
    private List<@NotBlank(message = "Ids must not be blank") String> ids;
}
