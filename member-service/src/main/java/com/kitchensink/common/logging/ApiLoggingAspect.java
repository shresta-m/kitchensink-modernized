package com.kitchensink.common.logging;

import com.kitchensink.common.exception.ApiException;
import com.kitchensink.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * One log line per API call, written around every {@code @RestController} method so no controller or service carries
 * logging code of its own. It records who called what, from where, how it ended and how long it took:
 *
 * <pre>
 * API PUT /api/v1/members/42/role actor=6641f0 ip=203.0.113.7 outcome=OK ms=12
 * API POST /api/v1/auth/login actor=anonymous ip=203.0.113.7 outcome=FAILED error=INVALID_CREDENTIALS ms=71
 * </pre>
 *
 * <p>What is deliberately left out: request and response bodies, parameters and the query string (a search term can be
 * an email or a phone number), headers and tokens. The path holds only ids. The trace id is added to every line by the
 * logging pattern, so a line can be matched to the error body and the trace. Route logger
 * {@code com.kitchensink.common.logging.ApiLoggingAspect} to its own index in Elasticsearch/Kibana for an access and
 * audit view.
 */
@Aspect
@Component
public class ApiLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

    static final String ANONYMOUS = "anonymous";

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logApiCall(ProceedingJoinPoint call) throws Throwable {
        long started = System.nanoTime();
        try {
            Object result = call.proceed();
            log.info("API {} actor={} ip={} outcome=OK ms={}", describe(call), actor(), clientIp(), elapsedMs(started));
            return result;
        } catch (ApiException e) {                      // an expected business error: the client did something wrong
            log.warn("API {} actor={} ip={} outcome=FAILED error={} ms={}",
                    describe(call), actor(), clientIp(), e.getCode(), elapsedMs(started));
            throw e;
        } catch (Throwable e) {                         // anything else: the stack trace is logged once by the exception handler
            log.error("API {} actor={} ip={} outcome=ERROR error={} ms={}",
                    describe(call), actor(), clientIp(), e.getClass().getSimpleName(), elapsedMs(started));
            throw e;
        }
    }

    /**
     * Logs a request the security filter turned away (401 or 403) before it reached a controller, in the same format,
     * so the API log has no blind spot. Called from the authentication entry point and the access-denied handler.
     */
    public static void logRejected(HttpServletRequest request, ErrorCode reason) {
        log.warn("API {} {} actor={} ip={} outcome=REJECTED error={}",
                request.getMethod(), request.getRequestURI(), actor(), request.getRemoteAddr(), reason);
    }

    private static String describe(ProceedingJoinPoint call) {
        if (call == null || call.getSignature() == null) {
            return "unknown";
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getMethod() + " " + attributes.getRequest().getRequestURI();
        }
        return call.getSignature().toShortString();     // not inside an HTTP request (a direct call): use the method name
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        return authentication.getName();
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
