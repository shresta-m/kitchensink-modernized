package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class MemberNotFoundException extends ApiException {
    public MemberNotFoundException(String id) {
        super(ErrorCode.MEMBER_NOT_FOUND, null, "Member not found: " + id);
    }
}
