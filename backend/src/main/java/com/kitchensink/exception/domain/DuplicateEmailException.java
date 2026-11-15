package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

public class DuplicateEmailException extends ConflictException {
    public DuplicateEmailException(Throwable cause) {
        super(ErrorCode.DUPLICATE_EMAIL, "This email is already registered.",
                List.of(new FieldViolation("email", "This email is already registered")), cause);
    }
}
