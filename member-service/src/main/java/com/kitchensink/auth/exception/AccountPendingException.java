package com.kitchensink.auth.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/**
 * The credentials are right but an administrator has not approved the registration yet. Only thrown after the password
 * matched, so it tells nobody that an account exists unless they already hold its password.
 */
public class AccountPendingException extends ApiException {
    public AccountPendingException() {
        super(ErrorCode.ACCOUNT_PENDING, null, "Your registration is waiting for administrator approval");
    }
}
