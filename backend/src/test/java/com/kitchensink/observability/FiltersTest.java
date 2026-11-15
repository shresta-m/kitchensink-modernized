package com.kitchensink.observability;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.exception.handler.ApiProblem;
import com.kitchensink.security.AuthRateLimitFilter;
import com.kitchensink.security.FixedWindowRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class FiltersTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }


    // ---- CorrelationIdFilter ------------------------------------------------------------------

    @Test
    void correlationIdFilterReusesASafeIncomingIdAndClearsMdcAfterwards() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.addHeader(CorrelationIdFilter.HEADER, "client-id_1.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        new CorrelationIdFilter().doFilter(req, res, (rq, rs) -> seen.set(MDC.get(ApiProblem.TRACE_KEY)));

        assertThat(seen.get()).isEqualTo("client-id_1.2");
        assertThat(res.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("client-id_1.2");
        assertThat(MDC.get(ApiProblem.TRACE_KEY)).isNull();
    }

    @Test
    void correlationIdFilterReplacesMissingOrUnsafeIds() throws Exception {
        for (String incoming : new String[] {null, "bad id with spaces", "x".repeat(65), "inj\r\nected"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
            if (incoming != null) {
                req.addHeader(CorrelationIdFilter.HEADER, incoming);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();

            new CorrelationIdFilter().doFilter(req, res, new MockFilterChain());

            assertThat(res.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{8}");
        }
    }


    // ---- AccessLogFilter ----------------------------------------------------------------------

    @Test
    void accessLogFilterSkipsHealthChecksOnly() {
        AccessLogFilter f = new AccessLogFilter();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health/liveness"))).isTrue();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/members"))).isFalse();
    }

    @Test
    void accessLogFilterPassesTheRequestThroughForAnonymousAndAuthenticatedUsers() throws Exception {
        AccessLogFilter f = new AccessLogFilter();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(new MockHttpServletRequest("GET", "/api/v1/members"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("jane", "pw", "ROLE_ADMIN"));
        MockFilterChain chain2 = new MockFilterChain();
        f.doFilter(new MockHttpServletRequest("POST", "/api/v1/members"), new MockHttpServletResponse(), chain2);
        assertThat(chain2.getRequest()).isNotNull();
    }

    @Test
    void accessLogFilterStillLogsWhenTheChainFails() {
        AccessLogFilter f = new AccessLogFilter();
        assertThatThrownBy(() -> f.doFilter(new MockHttpServletRequest("GET", "/boom"),
                new MockHttpServletResponse(), (rq, rs) -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class);
    }


    // ---- MdcTaskDecorator ---------------------------------------------------------------------

    @Test
    void mdcTaskDecoratorCarriesTheSubmittingThreadsContextAndRestoresTheRunners() {
        MDC.put("requestId", "from-request");
        AtomicReference<String> inside = new AtomicReference<>();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> inside.set(MDC.get("requestId")));

        MDC.put("requestId", "runner-own");
        decorated.run();

        assertThat(inside.get()).isEqualTo("from-request");
        assertThat(MDC.get("requestId")).isEqualTo("runner-own");
    }

    @Test
    void mdcTaskDecoratorHandlesEmptyContextsOnBothSides() {
        MDC.clear();
        AtomicReference<String> inside = new AtomicReference<>("unset");
        Runnable decorated = new MdcTaskDecorator().decorate(() -> inside.set(MDC.get("requestId")));

        decorated.run();

        assertThat(inside.get()).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void mdcTaskDecoratorClearsWhenNothingWasCapturedButRunnerHadContext() {
        MDC.clear();
        Runnable decorated = new MdcTaskDecorator().decorate(() -> { });
        MDC.put("requestId", "runner-own");

        decorated.run();

        assertThat(MDC.get("requestId")).isEqualTo("runner-own");
    }
}
