package com.kitchensink.member.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Columns a client may sort by. The API name is public; the property is what Mongo actually sorts on.
 * Adding a sortable column means adding one entry here and nothing else.
 */
public enum MemberSortField {
    NAME("name", "nameLower"),          // lowercase copy, so sorting ignores case
    EMAIL("email", "email"),
    ROLE("role", "role"),
    CREATED_AT("createdAt", "createdAt");

    private final String apiName;
    private final String property;

    MemberSortField(String apiName, String property) {
        this.apiName = apiName;
        this.property = property;
    }

    public String property() {
        return property;
    }

    public static Optional<MemberSortField> fromApiName(String name) {
        return Arrays.stream(values()).filter(f -> f.apiName.equals(name)).findFirst();
    }

    public static String allowedNames() {
        return String.join(", ", Arrays.stream(values()).map(f -> f.apiName).toList());
    }
}
