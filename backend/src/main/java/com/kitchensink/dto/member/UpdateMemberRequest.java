package com.kitchensink.dto.member;



import com.kitchensink.validation.MemberEmail;
import com.kitchensink.validation.MemberName;
import com.kitchensink.validation.MemberPhone;
import jakarta.validation.constraints.NotNull;

/** Full replace of a member's details; {@code version} enables optimistic locking. */
public record UpdateMemberRequest(
        @MemberName String name,
        @MemberEmail String email,
        @MemberPhone String phoneNumber,
        @NotNull(message = "Version is required") Long version) {}
