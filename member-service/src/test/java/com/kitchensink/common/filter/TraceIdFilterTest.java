package com.kitchensink.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final Tracer tracer = mock(Tracer.class);
    private final TraceIdFilter filter = new TraceIdFilter(tracer);

    private void currentTrace(String traceId) {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(traceId);
        when(span.context()).thenReturn(context);
        when(tracer.currentSpan()).thenReturn(span);
    }

    @Test
    void returnsTheTraceIdOfTheCurrentSpan() throws Exception {
        currentTrace("4bf92f3577b34da6a3ce929d0e0e4736");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/members"), response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void addsNoHeaderWhenTracingIsNotActive() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/members"), response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER)).isNull();
    }

    @Test
    void healthProbesAreHandledWithoutAnAccessLogLine() throws Exception {
        currentTrace("4bf92f3577b34da6a3ce929d0e0e4736");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/liveness"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void failuresPropagate() {
        when(tracer.currentSpan()).thenReturn(null);

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("boom"); })).isInstanceOf(ServletException.class);
    }
}
