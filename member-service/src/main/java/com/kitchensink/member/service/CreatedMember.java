package com.kitchensink.member.service;

import com.kitchensink.member.model.Member;

/** A member created by an admin, with the generated temporary password. It is returned once and never stored in clear. */
public record CreatedMember(Member member, String temporaryPassword) {
}
