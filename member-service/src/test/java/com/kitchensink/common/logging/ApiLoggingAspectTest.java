
package com.kitchensink.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kitchensink.common.exception.ErrorCode;
import com.kitchensink.member.exception.DuplicateEmailException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ApiLoggingAspectTest {

    private final ApiLoggingAspect aspect = new ApiLoggingAspect();
    private final ProceedingJoinPoint call = mock(ProceedingJoinPoint.class);
    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger logger;

    @BeforeEach
    void capture() {
        logger = (Logger) LoggerFactory.getLogger(ApiLoggingAspect.class);
        logger.setLevel(Level.INFO);

        lines.start();
        logger.addAppender(lines);

        // Configure a default signature for every mocked AOP invocation.
        Signature signature = mock(Signature.class);
        when(signature.toShortString())
                .thenReturn("MemberController.get(..)");

        when(call.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void clear() {
        logger.detachAppender(lines);
        lines.stop();

        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void inRequest(String method, String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        request.setQueryString("search=asha@example.com");

        // Query parameters must never reach application logs.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    private ILoggingEvent only() {
        assertThat(lines.list).hasSize(1);
        return lines.list.get(0);
    }

    @Test
    void aSuccessfulCallIsLoggedAtInfoWithActorAddressAndDuration()
            throws Throwable {

        inRequest("PUT", "/api/v1/members/42/role", "203.0.113.7");

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "member-9",
                        "pw",
                        "ROLE_ADMIN"
                )
        );

        when(call.proceed()).thenReturn("result");

        Object result = aspect.logApiCall(call);

        assertThat(result).isEqualTo("result");
        assertThat(only().getLevel()).isEqualTo(Level.INFO);

        assertThat(only().getFormattedMessage())
                .startsWith(
                        "API PUT /api/v1/members/42/role actor=member-9 ip=203.0.113.7 outcome=OK ms="
                )
                .doesNotContain("asha@example.com")
                .doesNotContain("search");
    }

    @Test
    void aBusinessErrorIsLoggedAtWarnWithItsCodeAndRethrown()
            throws Throwable {

        inRequest("POST", "/api/v1/members", "10.0.0.1");

        DuplicateEmailException failure = new DuplicateEmailException();

        when(call.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logApiCall(call))
                .isSameAs(failure);

        assertThat(only().getLevel()).isEqualTo(Level.WARN);

        assertThat(only().getFormattedMessage())
                .contains(
                        "API POST /api/v1/members actor=anonymous ip=10.0.0.1 outcome=FAILED error=DUPLICATE_EMAIL ms="
                );
    }

    @Test
    void anUnexpectedFailureIsLoggedAtErrorWithoutItsMessageAndRethrown()
            throws Throwable {

        inRequest("GET", "/api/v1/members/1", "10.0.0.1");

        IllegalStateException failure =
                new IllegalStateException("mongo password is hunter2");

        when(call.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logApiCall(call))
                .isSameAs(failure);

        assertThat(only().getLevel()).isEqualTo(Level.ERROR);

        assertThat(only().getFormattedMessage())
                .contains("outcome=ERROR error=IllegalStateException")
                .doesNotContain("hunter2");
    }

    @Test
    void outsideAnHttpRequestTheMethodNameAndNoAddressAreLogged()
            throws Throwable {

        aspect.logApiCall(call);

        assertThat(only().getFormattedMessage())
                .startsWith(
                        "API MemberController.get(..) actor=anonymous ip=null outcome=OK"
                );
    }

    @Test
    void aRequestTheSecurityFilterRejectedIsLoggedInTheSameFormat() {

        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/members");

        request.setRemoteAddr("10.0.0.9");

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(
                        "member-3",
                        "pw",
                        "ROLE_VIEWER"
                )
        );

        ApiLoggingAspect.logRejected(
                request,
                ErrorCode.ACCESS_DENIED
        );

        assertThat(only().getLevel()).isEqualTo(Level.WARN);

        assertThat(only().getFormattedMessage())
                .isEqualTo(
                        "API GET /api/v1/members actor=member-3 ip=10.0.0.9 outcome=REJECTED error=ACCESS_DENIED"
                );
    }

    @Test
    void theActorIsAnonymousForNoLoginAnUnauthenticatedTokenAndTheAnonymousToken()
            throws Throwable {

        // No authentication.
        aspect.logApiCall(call);

        // Unauthenticated token.
        TestingAuthenticationToken unauthenticated =
                new TestingAuthenticationToken("someone", "pw");

        unauthenticated.setAuthenticated(false);

        SecurityContextHolder.getContext()
                .setAuthentication(unauthenticated);

        aspect.logApiCall(call);

        // Anonymous authentication token.
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key",
                        "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                )
        );

        aspect.logApiCall(call);

        assertThat(lines.list)
                .hasSize(3)
                .allSatisfy(event ->
                        assertThat(event.getFormattedMessage())
                                .contains("actor=anonymous")
                );
    }
}
