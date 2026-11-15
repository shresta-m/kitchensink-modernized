package com.kitchensink.exception.domain;


import com.kitchensink.exception.ErrorCode;

/** Same response for unknown user and wrong password, so the API cannot be used to enumerate usernames. */
public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super(ErrorCode.UNAUTHENTICATED, "Invalid username or password.");
    }
}
