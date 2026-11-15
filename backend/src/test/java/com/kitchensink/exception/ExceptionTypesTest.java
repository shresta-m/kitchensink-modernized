package com.kitchensink.exception;




import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.exception.domain.AdminSignupDisabledException;
import com.kitchensink.exception.domain.ConflictException;
import com.kitchensink.exception.domain.DomainException;
import com.kitchensink.exception.domain.DuplicateEmailException;
import com.kitchensink.exception.domain.DuplicateUsernameException;
import com.kitchensink.exception.domain.ImportFileException;
import com.kitchensink.exception.domain.InvalidAdminCodeException;
import com.kitchensink.exception.domain.InvalidCredentialsException;
import com.kitchensink.exception.domain.InvalidQueryException;
import com.kitchensink.exception.domain.MemberNotFoundException;
import com.kitchensink.exception.domain.NotFoundException;
import com.kitchensink.exception.domain.VersionConflictException;
import com.kitchensink.exception.handler.ApiProblem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class ExceptionTypesTest {

    @Test
    void everyDomainExceptionCarriesItsCodeAndSafeMessage() {
        assertDomain(new MemberNotFoundException("1"), ErrorCode.MEMBER_NOT_FOUND, 0);
        assertDomain(new DuplicateEmailException(new RuntimeException("x")), ErrorCode.DUPLICATE_EMAIL, 1);
        assertDomain(new DuplicateUsernameException(new RuntimeException("x")), ErrorCode.DUPLICATE_USERNAME, 1);
        assertDomain(new VersionConflictException(), ErrorCode.VERSION_CONFLICT, 0);
        assertDomain(new VersionConflictException(new RuntimeException("x")), ErrorCode.VERSION_CONFLICT, 0);
        assertDomain(new InvalidQueryException("size", "bad"), ErrorCode.VALIDATION_FAILED, 1);
        assertDomain(new InvalidCredentialsException(), ErrorCode.UNAUTHENTICATED, 0);
        assertDomain(new InvalidAdminCodeException(), ErrorCode.INVALID_ADMIN_CODE, 1);
        assertDomain(new AdminSignupDisabledException(), ErrorCode.NOT_FOUND, 0);
        assertDomain(new ImportFileException(ErrorCode.INVALID_TEMPLATE, "bad file"), ErrorCode.INVALID_TEMPLATE, 0);
    }

    private static void assertDomain(DomainException ex, ErrorCode code, int violations) {
        assertThat(ex.code()).isEqualTo(code);
        assertThat(ex.clientMessage()).isEqualTo(ex.getMessage()).isNotBlank();
        assertThat(ex.violations()).hasSize(violations);
    }

    @Test
    void hierarchyGroupsNotFoundAndConflictErrors() {
        assertThat(new MemberNotFoundException("1")).isInstanceOf(NotFoundException.class);
        assertThat(new AdminSignupDisabledException()).isInstanceOf(NotFoundException.class);
        assertThat(new DuplicateEmailException(null)).isInstanceOf(ConflictException.class);
        assertThat(new DuplicateUsernameException(null)).isInstanceOf(ConflictException.class);
        assertThat(new VersionConflictException()).isInstanceOf(ConflictException.class);
    }

    @Test
    void duplicateExceptionsKeepTheirCauseButNotInTheMessage() {
        RuntimeException cause = new RuntimeException("E11000 index name");
        DuplicateEmailException ex = new DuplicateEmailException(cause);
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).doesNotContain("E11000");
        assertThat(ex.violations().get(0)).isEqualTo(new FieldViolation("email", "This email is already registered"));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void errorCodesHaveStableMetadata(ErrorCode code) {
        assertThat(code.status()).isNotNull();
        assertThat(code.slug()).isNotBlank().doesNotContain(" ");
        assertThat(code.title()).isNotBlank();
    }

    @Test
    void forStatusMapsFrameworkStatuses() {
        assertThat(ErrorCode.forStatus(401)).isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(ErrorCode.forStatus(403)).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ErrorCode.forStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.forStatus(405)).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.forStatus(413)).isEqualTo(ErrorCode.FILE_TOO_LARGE);
        assertThat(ErrorCode.forStatus(415)).isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE);
        assertThat(ErrorCode.forStatus(429)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.forStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.forStatus(503)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.forStatus(400)).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCode.TOO_MANY_REQUESTS.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void apiProblemBuildsTheWireShape() {
        MDC.put(ApiProblem.TRACE_KEY, "abc123");
        try {
            ApiProblem p = ApiProblem.of(ErrorCode.VALIDATION_FAILED, "bad", "/x",
                    List.of(new FieldViolation("name", "required")));

            assertThat(p.type()).isEqualTo("https://kitchensink.example/problems/validation-failed");
            assertThat(p.status()).isEqualTo(400);
            assertThat(p.code()).isEqualTo("VALIDATION_FAILED");
            assertThat(p.traceId()).isEqualTo("abc123");
            assertThat(p.instance()).isEqualTo("/x");
            assertThat(p.errors()).hasSize(1);
        } finally {
            MDC.remove(ApiProblem.TRACE_KEY);
        }
    }

    @Test
    void apiProblemOmitsEmptyOptionalMembers() {
        ApiProblem p = ApiProblem.of(ErrorCode.NOT_FOUND, "gone", " ");

        assertThat(p.instance()).isNull();
        assertThat(p.errors()).isNull();
        assertThat(p.traceId()).isNull();
        assertThat(ApiProblem.of(ErrorCode.NOT_FOUND, "gone", null, null).errors()).isNull();
    }
}
