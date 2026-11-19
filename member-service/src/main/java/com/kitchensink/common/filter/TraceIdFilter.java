package com.kitchensink.common.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hands the trace id to the caller and writes one access-log line per request. The trace itself is created by
 * Micrometer Tracing (it reads the caller's W3C {@code traceparent} header, or starts a new trace) in a filter that
 * runs just before this one, so the id here is the same one that appears in the logs, in error bodies and in the
 * collector. It runs ahead of Spring Security, so 401 and 403 responses carry it too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)        // after the observation filter (HIGHEST_PRECEDENCE + 1), before security
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    /** Key under which Micrometer Tracing puts the trace id in the logging context. */
    public static final String MDC_KEY = "traceId";
    private static final String HEALTH_PREFIX = "/actuator";

    private final Tracer tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Span span = tracer.currentSpan();
        if (span != null) {
            response.setHeader(HEADER, span.context().traceId());
        }
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith(HEALTH_PREFIX)) {      // probes poll every few seconds
                log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - start) / 1_000_000);
            }
        }
    }
}
