package com.kitchensink.exception.handler;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.FieldViolation;
import com.kitchensink.exception.domain.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Single place that turns every failure into an {@link ApiProblem}. Handlers are grouped by origin:
 * the domain hierarchy (one handler for all of it), request-shape errors, security, framework errors
 * and a catch-all. Internals (stack traces, framework or driver messages) are logged with the trace id
 * and never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- domain ---------------------------------------------------------------------------------

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiProblem> domain(DomainException ex, HttpServletRequest req) {
        log.debug("Domain error code={} path={}", ex.code(), req.getRequestURI());
        return respond(ex.code(), ex.clientMessage(), req, ex.violations(), HttpHeaders.EMPTY);
    }

    // ---- request shape --------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", req, errors, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiProblem> unreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        List<FieldViolation> errors = ex.getCause() instanceof UnrecognizedPropertyException upe
                ? List.of(new FieldViolation(upe.getPropertyName(), "Unknown field"))
                : List.of();
        return respond(ErrorCode.VALIDATION_FAILED, "Request body is missing or malformed.", req, errors,
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiProblem> uploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return respond(ErrorCode.FILE_TOO_LARGE, "The uploaded file is too large.", req, List.of(), HttpHeaders.EMPTY);
    }

    // ---- security errors raised inside controllers (filter-level ones use ProblemResponseWriter) -

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiProblem> unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return respond(ErrorCode.UNAUTHENTICATED, "Authentication is required.", req, List.of(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiProblem> forbidden(AccessDeniedException ex, HttpServletRequest req) {
        return respond(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.", req, List.of(),
                HttpHeaders.EMPTY);
    }

    // ---- everything else -----------------------------------------------------------------------

    /**
     * Spring MVC exceptions (404 route, 405, 415, missing parameter, type mismatch, ...) implement
     * {@link ErrorResponse}; their status and headers (e.g. Allow) are kept, their text is replaced by ours.
     * Anything else is a bug: logged in full, answered with a generic 500.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> fallback(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er && !er.getStatusCode().is5xxServerError()) {
            HttpStatusCode status = er.getStatusCode();
            ErrorCode code = ErrorCode.forStatus(status.value());
            return respond(code, code.title() + ".", req, List.of(), er.getHeaders());
        }
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", req, List.of(), HttpHeaders.EMPTY);
    }

    private static ResponseEntity<ApiProblem> respond(
            ErrorCode code, String detail, HttpServletRequest req, List<FieldViolation> errors, HttpHeaders headers) {
        return ResponseEntity.status(code.status())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(code, detail, req.getRequestURI(), errors));
    }
}
