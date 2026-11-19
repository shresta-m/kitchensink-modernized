package com.kitchensink.member.exception;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;

public class ExportTooLargeException extends ApiException {
    public ExportTooLargeException(int limit) {
        super(ErrorCode.EXPORT_TOO_LARGE, null,
                "Too many members to export (limit " + limit + "). Narrow your search or filters.");
    }
}
