package com.kitchensink.exception.handler;



import com.fasterxml.jackson.annotation.JsonInclude;
import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import java.util.List;
import org.slf4j.MDC;

/**
 * Error body in the RFC 9457 shape ({@code application/problem+json}) plus our members {@code code}, {@code traceId}
 * and {@code errors[]}. A plain record rather than Spring's ProblemDetail so the wire contract the frontend depends
 * on is owned here and does not shift with framework or Jackson major versions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String traceId,
        List<FieldViolation> errors) {

    public static final String TRACE_KEY = "requestId";
    private static final String TYPE_BASE = "https://kitchensink.example/problems/";

    public static ApiProblem of(ErrorCode code, String detail, String path) {
        return of(code, detail, path, List.of());
    }

    public static ApiProblem of(ErrorCode code, String detail, String path, List<FieldViolation> errors) {
        return new ApiProblem(
                TYPE_BASE + code.slug(),
                code.title(),
                code.status().value(),
                detail,
                path == null || path.isBlank() ? null : path,
                code.name(),
                MDC.get(TRACE_KEY),
                errors == null || errors.isEmpty() ? null : errors);
    }
}
