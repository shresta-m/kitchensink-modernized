package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

/**
 * Root of every business-rule failure. Each instance carries a stable {@link ErrorCode} and a message that is
 * safe to show to the client, so one handler can map the whole hierarchy and no subclass needs its own mapping.
 * Framework and database exceptions never extend this: they are translated at the layer that sees them.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldViolation> violations;

    protected DomainException(ErrorCode code, String clientMessage) {
        this(code, clientMessage, List.of(), null);
    }

    protected DomainException(ErrorCode code, String clientMessage, List<FieldViolation> violations, Throwable cause) {
        super(clientMessage, cause);
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public ErrorCode code() {
        return code;
    }

    /** Client-safe description; equal to {@link #getMessage()}. */
    public String clientMessage() {
        return getMessage();
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
