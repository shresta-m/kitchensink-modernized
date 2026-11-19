package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class DuplicateEmailException extends ApiException {
    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, "email", "An account with this email already exists");
    }
}
