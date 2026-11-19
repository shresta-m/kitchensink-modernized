package com.kitchensink.member.dto;

import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/** Public view of a member. Never contains the password hash. */
@Getter
@Builder
public class MemberResponse {

    private final String id;
    private final String name;
    private final String email;
    private final String countryCode;
    private final String phone;
    private final Long version;
    private final Role role;
    private final MemberStatus status;
    private final Instant createdAt;

    public static MemberResponse from(Member m) {
        return MemberResponse.builder()
                .id(m.getId()).version(m.getVersion()).name(m.getName()).email(m.getEmail())
                .countryCode(m.getCountryCode()).phone(m.getPhoneNumber())
                .role(m.getRole()).status(m.getStatus())
                .createdAt(m.getCreatedAt()).build();
    }
}
