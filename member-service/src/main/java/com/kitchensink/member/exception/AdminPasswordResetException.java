package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** An administrator's password cannot be reset by another administrator (separation of duties). */
public class AdminPasswordResetException extends ApiException {
    public AdminPasswordResetException() {
        super(ErrorCode.ADMIN_PASSWORD_RESET_FORBIDDEN, null,
                "An administrator's password cannot be reset. Demote the administrator first.");
    }
}
