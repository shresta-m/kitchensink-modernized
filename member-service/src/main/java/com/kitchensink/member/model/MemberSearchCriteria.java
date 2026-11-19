package com.kitchensink.member.model;

/** Filters for listing members. Every field is optional; null means "do not filter on this". */
public record MemberSearchCriteria(String search, Role role, MemberStatus status) {
}
