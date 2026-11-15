package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

/** The request is valid but clashes with current state (uniqueness, concurrent edit). Maps to 409. */
public abstract class ConflictException extends DomainException {
    protected ConflictException(ErrorCode code, String clientMessage, List<FieldViolation> violations, Throwable cause) {
        super(code, clientMessage, violations, cause);
    }
}
