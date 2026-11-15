package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

/** The admin sign-up code was wrong. The message never says how it differed. */
public class InvalidAdminCodeException extends DomainException {
    public InvalidAdminCodeException() {
        super(ErrorCode.INVALID_ADMIN_CODE, "The admin sign-up code is not valid.",
                List.of(new FieldViolation("adminCode", "Invalid admin sign-up code")), null);
    }
}
