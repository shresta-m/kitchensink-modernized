package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** The client edited an older copy of the member. It must reload and apply its change again. */
public class StaleVersionException extends ApiException {
    public StaleVersionException() {
        super(ErrorCode.CONCURRENT_MODIFICATION, "version",
                "This member was changed by someone else. Reload and try again.");
    }
}
