package com.kitchensink.member.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResetPasswordResponse {

    /** Shown once. Not stored in readable form. */
    private final String temporaryPassword;

    @Override
    public String toString() {
        return "ResetPasswordResponse[temporaryPassword=***]";
    }
}
