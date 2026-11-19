package com.kitchensink.common.dto;

import com.kitchensink.common.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** The one error shape every failing request returns. {@code code} is stable and meant for the client to branch on. */
@Getter
@Builder
public class ApiError {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final ErrorCode code;
    private final String message;
    private final String path;
    /** The trace id of the request: same as the X-Trace-Id response header, the log lines, and the trace in the collector. */
    private final String traceId;
    private final List<FieldViolation> fieldErrors;
}
