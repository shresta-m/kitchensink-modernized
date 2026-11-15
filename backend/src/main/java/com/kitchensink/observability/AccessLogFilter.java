package com.kitchensink.observability;



import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One line per request with method, path, status, duration and user. Deliberately NOT a Spring bean:
 * it is added inside the security chain (after authentication) so the user is known. Never logs bodies
 * or query strings (they can contain personal data).
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String user = auth != null && auth.isAuthenticated() ? auth.getName() : "-";
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("method={} path={} status={} durationMs={} user={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), ms, user);
        }
    }
}
