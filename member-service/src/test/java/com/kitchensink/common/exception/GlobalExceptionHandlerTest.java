package com.kitchensink.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.auth.exception.AccountPendingException;
import com.kitchensink.auth.exception.InvalidAdminCodeException;
import com.kitchensink.auth.exception.InvalidCredentialsException;
import com.kitchensink.auth.exception.InvalidPasswordException;
import com.kitchensink.common.dto.ApiError;
import com.kitchensink.common.filter.TraceIdFilter;
import com.kitchensink.member.exception.AdminDeletionException;
import com.kitchensink.member.exception.AdminPasswordResetException;
import com.kitchensink.security.throttle.TooManyAttemptsException;
import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.exception.ExportTooLargeException;
import com.kitchensink.member.exception.InvalidSortException;
import com.kitchensink.member.exception.LastAdminException;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.member.exception.NotPendingApprovalException;
import com.kitchensink.member.exception.PageTooDeepException;
import com.kitchensink.member.exception.StaleVersionException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Direct calls for the branches that are awkward to trigger over HTTP. The rest is covered by the controller tests. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");

    @SuppressWarnings("unused")
    void sample(String ignored) {
        // target of the MethodParameter below
    }

    private void assertError(ResponseEntity<ApiError> response, HttpStatus status, ErrorCode code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status.value());
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(status.value());
        assertThat(body.getError()).isEqualTo(status.getReasonPhrase());
        assertThat(body.getCode()).isEqualTo(code);
        assertThat(body.getPath()).isEqualTo("/api/v1/members");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    void validationErrorsKeepTheFirstMessagePerFieldAndIncludeObjectLevelErrors() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "target");
        result.addError(new FieldError("target", "name", "first"));
        result.addError(new FieldError("target", "name", "second"));
        result.addError(new ObjectError("target", "object level"));
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        var ex = new MethodArgumentNotValidException(new MethodParameter(method, 0), result);

        ResponseEntity<ApiError> response = handler.handleValidation(ex, request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
        assertThat(response.getBody().getFieldErrors()).extracting("field", "message")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("name", "first"),
                        org.assertj.core.groups.Tuple.tuple("target", "object level"));
    }

    @Test
    void typeMismatchNamesTheParameter() throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class);
        var ex = new MethodArgumentTypeMismatchException("BOSS", Enum.class, "role", new MethodParameter(method, 0), null);

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex, request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED);
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid value for 'role'");
        assertThat(response.getBody().getFieldErrors().get(0).getField()).isEqualTo("role");
    }

    @Test
    void optimisticLockIsAConflict() {
        ResponseEntity<ApiError> response = handler.handleOptimisticLock(
                new OptimisticLockingFailureException("stale"), request);

        assertError(response, HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION);
        assertThat(response.getBody().getFieldErrors()).isEmpty();
    }

    @Test
    void unreadableBodyIsABadRequest() {
        var ex = new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]));

        assertError(handler.handleUnreadable(ex, request), HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void errorBodyCarriesTheCurrentTraceId() {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-1234");
        try {
            assertThat(handler.handleApi(new DuplicateEmailException(), request).getBody().getTraceId())
                    .isEqualTo("trace-1234");
        } finally {
            MDC.remove(TraceIdFilter.MDC_KEY);
        }
        assertThat(handler.handleApi(new DuplicateEmailException(), request).getBody().getTraceId()).isNull();
    }

    @Test
    void everyDomainExceptionMapsToItsHttpStatus() {
        assertStatus(new AdminDeletionException(), HttpStatus.CONFLICT);
        assertStatus(new StaleVersionException(), HttpStatus.CONFLICT);
        assertStatus(new DuplicateEmailException(), HttpStatus.CONFLICT);
        assertStatus(new LastAdminException(), HttpStatus.CONFLICT);
        assertStatus(new NotPendingApprovalException("1"), HttpStatus.CONFLICT);
        assertStatus(new AccountPendingException(), HttpStatus.FORBIDDEN);
        assertStatus(new AdminPasswordResetException(), HttpStatus.CONFLICT);
        assertStatus(new TooManyAttemptsException(5), HttpStatus.TOO_MANY_REQUESTS);
        assertStatus(new ExportTooLargeException(50_000), HttpStatus.BAD_REQUEST);
        assertStatus(new InvalidAdminCodeException(), HttpStatus.BAD_REQUEST);
        assertStatus(new InvalidPasswordException("newPassword", "bad"), HttpStatus.BAD_REQUEST);
        assertStatus(new InvalidSortException("bad"), HttpStatus.BAD_REQUEST);
        assertStatus(new PageTooDeepException(10_000), HttpStatus.BAD_REQUEST);
        assertStatus(new InvalidCredentialsException(), HttpStatus.UNAUTHORIZED);
        assertStatus(new MemberNotFoundException("9"), HttpStatus.NOT_FOUND);
    }

    private void assertStatus(ApiException ex, HttpStatus expected) {
        assertError(handler.handleApi(ex, request), expected, ex.getCode());
    }

    @Test
    void aLockoutCarriesRetryAfterInWholeSeconds() {
        ResponseEntity<ApiError> response = handler.handleTooManyAttempts(new TooManyAttemptsException(123), request);

        assertError(response, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("123");
    }

    @Test
    void apiExceptionWithAndWithoutField() {
        ResponseEntity<ApiError> withField = handler.handleApi(new DuplicateEmailException(), request);
        assertError(withField, HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL);
        assertThat(withField.getBody().getFieldErrors().get(0).getField()).isEqualTo("email");

        ResponseEntity<ApiError> withoutField = handler.handleApi(new MemberNotFoundException("9"), request);
        assertError(withoutField, HttpStatus.NOT_FOUND, ErrorCode.MEMBER_NOT_FOUND);
        assertThat(withoutField.getBody().getFieldErrors()).isEmpty();
    }

    @Test
    void duplicateKeyIsAConflict() {
        assertError(handler.handleDuplicateKey(new DuplicateKeyException("dup"), request),
                HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void springErrorResponsesKeepTheirStatus() {
        var withDetail = new ErrorResponseException(HttpStatus.METHOD_NOT_ALLOWED);
        withDetail.getBody().setDetail("Method not supported");
        ResponseEntity<ApiError> response = handler.handleAny(withDetail, request);
        assertError(response, HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.REQUEST_REJECTED);
        assertThat(response.getBody().getMessage()).isEqualTo("Method not supported");

        ResponseEntity<ApiError> noDetail = handler.handleAny(new ErrorResponseException(HttpStatus.GONE), request);
        assertError(noDetail, HttpStatus.GONE, ErrorCode.REQUEST_REJECTED);
        assertThat(noDetail.getBody().getMessage()).isEqualTo("Request failed");
    }

    @Test
    void authenticationFailuresAre401WithoutDetails() {
        ResponseEntity<ApiError> response = handler.handleUnauthenticated(
                new BadCredentialsException("secret detail"), request);

        assertError(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED);
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
    }

    @Test
    void accessDeniedIs403() {
        assertError(handler.handleAccessDenied(new AccessDeniedException("no"), request),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED);
    }

    @Test
    void invalidCredentialsAndPasswordErrors() {
        assertError(handler.handleApi(new InvalidCredentialsException(), request),
                HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS);
        ResponseEntity<ApiError> response = handler.handleApi(new InvalidPasswordException("newPassword", "bad"), request);
        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PASSWORD);
        assertThat(response.getBody().getFieldErrors().get(0).getField()).isEqualTo("newPassword");
    }

    @Test
    void unknownFailuresAreHiddenBehindAGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleAny(new IllegalStateException("db password is x"), request);

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("password");
    }
}
