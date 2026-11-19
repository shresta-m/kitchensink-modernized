package com.kitchensink.lookup.controller;

import com.kitchensink.lookup.dto.LookupOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** The lookups API contract and its OpenAPI documentation. {@link LookupController} implements it. */
@Tag(name = "Lookups", description = "Value lists for filters and forms")
@SecurityRequirement(name = "bearerAuth")
public interface LookupControllerInterface {

    @Operation(summary = "All roles")
    List<LookupOption> roles();

    @Operation(summary = "All member statuses")
    List<LookupOption> statuses();
}
