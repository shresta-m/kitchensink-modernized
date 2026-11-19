package com.kitchensink.auth.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** Wrong email or password. One message for both, so the response never reveals which accounts exist. */
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, null, "Invalid email or password");
    }
}
