package com.kitchensink.exception.domain;


import com.kitchensink.exception.ErrorCode;

/** A requested resource does not exist. Maps to 404. */
public abstract class NotFoundException extends DomainException {
    protected NotFoundException(ErrorCode code, String clientMessage) {
        super(code, clientMessage);
    }
}
