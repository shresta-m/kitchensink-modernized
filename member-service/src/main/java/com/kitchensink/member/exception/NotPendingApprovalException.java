package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

/** Approval was requested for a member who is not waiting for it. */
public class NotPendingApprovalException extends ApiException {
    public NotPendingApprovalException(String id) {
        super(ErrorCode.NOT_PENDING_APPROVAL, null, "Member " + id + " is not waiting for approval");
    }
}
