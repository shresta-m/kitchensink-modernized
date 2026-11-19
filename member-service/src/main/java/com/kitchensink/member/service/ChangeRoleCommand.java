package com.kitchensink.member.service;

import com.kitchensink.member.model.Role;

/** Role change. {@code version} is the one the client loaded. */
public record ChangeRoleCommand(Role role, Long version) {
}
