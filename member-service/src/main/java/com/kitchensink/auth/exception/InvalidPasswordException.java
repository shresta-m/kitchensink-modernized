package com.kitchensink.auth.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** A password change was refused. The field says which input to mark. */
public class InvalidPasswordException extends ApiException {
    public InvalidPasswordException(String field, String message) {
        super(ErrorCode.INVALID_PASSWORD, field, message);
    }
}
