package com.kitchensink.member.service;

import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.model.Role;

/** Admin "add member": the profile plus the role to grant. */
public record CreateMemberCommand(MemberDetails details, Role role) {
}
