package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** Thrown when an operation would leave the system without any administrator. */
public class LastAdminException extends ApiException {
    public LastAdminException() {
        super(ErrorCode.LAST_ADMIN, null, "At least one administrator must remain");
    }
}
