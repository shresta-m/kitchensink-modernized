package com.kitchensink.member.dto;

import lombok.Builder;
import lombok.Getter;

/** The created member plus the one-time temporary password. The password is not stored in readable form. */
@Getter
@Builder
public class CreateMemberResponse {

    private final MemberResponse member;
    private final String temporaryPassword;

    @Override
    public String toString() {
        return "CreateMemberResponse[member=" + member + ", temporaryPassword=***]";
    }
}
