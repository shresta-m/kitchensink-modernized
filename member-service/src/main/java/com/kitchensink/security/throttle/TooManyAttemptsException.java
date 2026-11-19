package com.kitchensink.security.throttle;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** A key is locked out. {@link com.kitchensink.common.exception.GlobalExceptionHandler} maps it to 429 with Retry-After. */
public class TooManyAttemptsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_ATTEMPTS, null, "Too many attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
