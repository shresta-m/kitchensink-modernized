package com.kitchensink.member.service;

import com.kitchensink.member.exception.InvalidSortException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Turns "field,direction" (for example "name,asc") into a Sort, rejecting anything outside the whitelist. */
@Component
public class MemberSortParser {

    public Sort parse(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length > 2) {
            throw invalid(raw);
        }
        MemberSortField field = MemberSortField.fromApiName(parts[0].trim()).orElseThrow(() -> invalid(raw));
        Sort.Direction direction = parts.length == 2
                ? Sort.Direction.fromOptionalString(parts[1].trim()).orElseThrow(() -> invalid(raw))
                : Sort.Direction.ASC;
        // "id" as a final tie-breaker keeps page boundaries stable when many rows share the same sort value.
        return Sort.by(direction, field.property()).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static InvalidSortException invalid(String raw) {
        return new InvalidSortException("Cannot sort by '" + raw + "'. Use field,asc|desc with field one of: "
                + MemberSortField.allowedNames());
    }
}
