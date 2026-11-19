package com.kitchensink.member.service;

import com.kitchensink.member.model.MemberDetails;

/** Profile edit. {@code version} is the one the client loaded; a different current value means a concurrent edit. */
public record UpdateMemberCommand(MemberDetails details, Long version) {
}
