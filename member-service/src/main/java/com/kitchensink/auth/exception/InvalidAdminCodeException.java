package com.kitchensink.auth.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class InvalidAdminCodeException extends ApiException {
    public InvalidAdminCodeException() {
        super(ErrorCode.INVALID_ADMIN_CODE, "adminCode", "Admin code is not valid");
    }
}
