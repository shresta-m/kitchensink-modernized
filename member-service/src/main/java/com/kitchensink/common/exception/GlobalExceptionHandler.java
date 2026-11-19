package com.kitchensink.common.exception;

import com.kitchensink.common.dto.ApiError;
import com.kitchensink.common.dto.FieldViolation;
import com.kitchensink.common.filter.TraceIdFilter;
import com.kitchensink.security.throttle.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** The HTTP status of each business error code. The exceptions themselves carry no HTTP knowledge. */
    private static final Map<ErrorCode, HttpStatus> STATUS_BY_CODE = new EnumMap<>(Map.ofEntries(
            Map.entry(ErrorCode.ACCOUNT_PENDING, HttpStatus.FORBIDDEN),
            Map.entry(ErrorCode.ADMIN_MUST_BE_DEMOTED, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.ADMIN_PASSWORD_RESET_FORBIDDEN, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.CONCURRENT_MODIFICATION, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.EXPORT_TOO_LARGE, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INVALID_ADMIN_CODE, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED),
            Map.entry(ErrorCode.INVALID_PASSWORD, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.INVALID_SORT, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.LAST_ADMIN, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.MEMBER_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(ErrorCode.NOT_PENDING_APPROVAL, HttpStatus.CONFLICT),
            Map.entry(ErrorCode.PAGE_TOO_DEEP, HttpStatus.BAD_REQUEST),
            Map.entry(ErrorCode.TOO_MANY_ATTEMPTS, HttpStatus.TOO_MANY_REQUESTS)
    ));

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> byField = new LinkedHashMap<>();   // one message per field
        ex.getBindingResult().getFieldErrors().forEach(e -> byField.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(e -> byField.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        List<FieldViolation> violations = byField.entrySet().stream()
                .map(e -> new FieldViolation(e.getKey(), e.getValue())).toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", violations, req);
    }

    /** Constraint violations on @RequestParam / @PathVariable, for example size=0 or page=-1. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(e -> violations.add(new FieldViolation(name, e.getDefaultMessage())));
        });
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", violations, req);
    }

    /** A value that cannot be converted, for example role=BOSS or status=FOO. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String message = "Invalid value for '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message,
                List.of(new FieldViolation(ex.getName(), message)), req);
    }

    /** Someone else saved the same document first (@Version mismatch). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "This record was changed by someone else. Reload and try again.", List.of(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.debug("Unreadable request body", ex);
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Malformed or unreadable request body", List.of(), req);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getField() == null
                ? List.of() : List.of(new FieldViolation(ex.getField(), ex.getMessage()));
        return build(STATUS_BY_CODE.getOrDefault(ex.getCode(), HttpStatus.INTERNAL_SERVER_ERROR), ex.getCode(), ex.getMessage(), violations, req);
    }

    /** A brute-force lockout: 429, and Retry-After tells the client (and well-behaved bots) when to come back. */
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyAttemptsException ex, HttpServletRequest req) {
        ResponseEntity<ApiError> response = handleApi(ex, req);
        return ResponseEntity.status(response.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    /** Safety net for a race on the unique email index that slipped past the service-level check. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE, "Resource already exists", List.of(), req);
    }

    /** Raised by the security filters and delegated here, so every 401 has the same JSON shape. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleUnauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Sign in to continue", List.of(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                "You do not have permission to do this", List.of(), req);
    }

    /** Spring's own errors (404, 405, 415 ...) keep their real status; anything else is a 500 with no internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            String detail = er.getBody().getDetail();
            return build(HttpStatus.valueOf(er.getStatusCode().value()), ErrorCode.REQUEST_REJECTED,
                    detail != null ? detail : "Request failed", List.of(), req);
        }
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong. Try again later.", List.of(), req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           List<FieldViolation> violations, HttpServletRequest req) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now()).status(status.value()).error(status.getReasonPhrase())
                .code(code).message(message).path(req.getRequestURI()).traceId(MDC.get(TraceIdFilter.MDC_KEY)).fieldErrors(violations)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
