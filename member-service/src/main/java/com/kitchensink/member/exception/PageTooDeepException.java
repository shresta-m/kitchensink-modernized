package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class PageTooDeepException extends ApiException {
    public PageTooDeepException(int maxOffset) {
        super(ErrorCode.PAGE_TOO_DEEP, "page",
                "Page is too far into the results (limit " + maxOffset + " rows). Narrow your search or filters.");
    }
}
