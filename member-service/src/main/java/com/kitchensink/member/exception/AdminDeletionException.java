package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** Administrators are never deleted directly: demote first, so the last-admin guard runs in one place. */
public class AdminDeletionException extends ApiException {
    public AdminDeletionException() {
        super(ErrorCode.ADMIN_MUST_BE_DEMOTED, null,
                "Administrators cannot be deleted. Change the role to Viewer first.");
    }
}
