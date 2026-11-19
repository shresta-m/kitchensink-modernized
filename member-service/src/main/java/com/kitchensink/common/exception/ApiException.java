package com.kitchensink.common.exception;

/**
 * Base for expected business errors. It knows its error code and the field to mark, but nothing about HTTP:
 * {@link GlobalExceptionHandler} decides the status for each code.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String field;   // optional: lets the UI show the error under one input

    protected ApiException(ErrorCode code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode getCode() { return code; }
    public String getField() { return field; }
}
