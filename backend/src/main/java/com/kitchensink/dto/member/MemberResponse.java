package com.kitchensink.dto.member;



import com.kitchensink.model.Member;
import java.time.Instant;

public record MemberResponse(
        String id,
        String name,
        String email,
        String phoneNumber,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getName(), m.getEmail(), m.getPhoneNumber(),
                m.getCreatedAt(), m.getUpdatedAt(), m.getVersion() == null ? 0 : m.getVersion());
    }
}
