package com.kitchensink.auth.dto;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {

    private final String id;
    private final String name;
    private final String email;
    private final Role role;
    private final MemberStatus status;

    public static RegisterResponse from(Member m) {
        return RegisterResponse.builder().id(m.getId()).name(m.getName()).email(m.getEmail()).role(m.getRole()).status(m.getStatus()).build();
    }
}
