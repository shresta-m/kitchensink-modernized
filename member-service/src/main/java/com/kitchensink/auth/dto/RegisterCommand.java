package com.kitchensink.auth.dto;

import com.kitchensink.member.model.MemberDetails;

/** Self-registration. A missing or blank {@code adminCode} means VIEWER. {@code clientIp} feeds the admin-code lockout. */
public record RegisterCommand(MemberDetails details, String password, String adminCode, String clientIp) {
}
