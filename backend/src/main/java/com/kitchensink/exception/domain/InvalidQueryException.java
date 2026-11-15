package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

/** Bad paging, sorting or search input. Maps to 400 with a field-level error. */
public class InvalidQueryException extends DomainException {
    public InvalidQueryException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, "Invalid query parameter.", List.of(new FieldViolation(field, message)), null);
    }
}
