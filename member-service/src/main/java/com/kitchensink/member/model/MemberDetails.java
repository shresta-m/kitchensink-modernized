package com.kitchensink.member.model;

/** The editable contact fields of a member, as received from a request. Normalization happens in Member. */
public record MemberDetails(String name, String email, String countryCode, String phoneNumber) {
}