package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;

public class DuplicateUsernameException extends ConflictException {
    public DuplicateUsernameException(Throwable cause) {
        super(ErrorCode.DUPLICATE_USERNAME, "This username is already taken.",
                List.of(new FieldViolation("username", "This username is already taken")), cause);
    }
}
