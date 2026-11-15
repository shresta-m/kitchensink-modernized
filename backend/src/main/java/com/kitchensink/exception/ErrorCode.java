package com.kitchensink.exception;



import org.springframework.http.HttpStatus;

/** Stable machine-readable error codes returned in the {@code code} field of every problem response. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "validation-failed", "Validation failed"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not-found", "Resource not found"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "member-not-found", "Member not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed"),
    INVALID_ADMIN_CODE(HttpStatus.FORBIDDEN, "invalid-admin-code", "Invalid admin sign-up code"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "duplicate-email", "Email already registered"),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "duplicate-username", "Username already taken"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too many requests"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "version-conflict", "Member was modified by someone else"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "file-too-large", "File too large"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-file-type", "Unsupported file type"),
    INVALID_TEMPLATE(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-template", "Invalid import file"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error");

    private final HttpStatus status;
    private final String slug;
    private final String title;

    ErrorCode(HttpStatus status, String slug, String title) {
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String slug() {
        return slug;
    }

    public String title() {
        return title;
    }

    /** Best-effort mapping for framework-generated errors (405, 413, 415, ...). */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> FILE_TOO_LARGE;
            case 415 -> UNSUPPORTED_FILE_TYPE;
            case 429 -> TOO_MANY_REQUESTS;
            default -> status >= 500 ? INTERNAL_ERROR : VALIDATION_FAILED;
        };
    }
}
