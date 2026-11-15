package com.kitchensink.dto.member;



import com.kitchensink.validation.MemberEmail;
import com.kitchensink.validation.MemberName;
import com.kitchensink.validation.MemberPhone;

/** Create-member request body. The same constraints are applied to every imported spreadsheet row. */
public record MemberRequest(
        @MemberName String name,
        @MemberEmail String email,
        @MemberPhone String phoneNumber) {}
