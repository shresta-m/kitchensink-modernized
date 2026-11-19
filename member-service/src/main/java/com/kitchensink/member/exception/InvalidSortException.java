package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class InvalidSortException extends ApiException {
    public InvalidSortException(String message) {
        super(ErrorCode.INVALID_SORT, "sort", message);
    }
}
