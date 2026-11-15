package com.kitchensink.exception.domain;



import com.kitchensink.exception.ErrorCode;
import java.util.List;

public class VersionConflictException extends ConflictException {
    public VersionConflictException() {
        this(null);
    }

    public VersionConflictException(Throwable cause) {
        super(ErrorCode.VERSION_CONFLICT, "The member was changed since you loaded it. Reload and try again.",
                List.of(), cause);
    }
}
