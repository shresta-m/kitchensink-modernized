package com.kitchensink.security;



import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.handler.ApiProblem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Slows down password guessing and mass sign-up: limits POST /auth/login, /auth/register and /auth/register-admin per client
 * address. Not a bean: SecurityConfig adds it to the security chain after CORS, so a 429 still carries CORS headers.
 * Uses the socket address; behind a reverse proxy enable {@code server.forward-headers-strategy} so it is the real client.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final FixedWindowRateLimiter limiter;
    private final ObjectMapper mapper;

    public AuthRateLimitFilter(FixedWindowRateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !HttpMethod.POST.matches(request.getMethod())
                || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")
                        || path.equals("/api/v1/auth/register-admin"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getRemoteAddr() + "|" + req.getRequestURI();
        if (limiter.tryAcquire(key)) {
            chain.doFilter(req, res);
            return;
        }
        log.warn("Rate limit exceeded path={}", req.getRequestURI());
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(limiter.retryAfterSeconds()));
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiProblem.of(ErrorCode.TOO_MANY_REQUESTS,
                "Too many attempts. Please wait a moment and try again.", req.getRequestURI()));
    }
}
