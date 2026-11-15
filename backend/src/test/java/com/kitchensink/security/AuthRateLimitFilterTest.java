package com.kitchensink.security;




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

class AuthRateLimitFilterTest {

    @AfterEach
    void cleanUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }


    // ---- AuthRateLimitFilter ------------------------------------------------------------------

    private static AuthRateLimitFilter rateLimit(int limit) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:10Z"), ZoneOffset.UTC);
        return new AuthRateLimitFilter(new FixedWindowRateLimiter(limit, 60, clock), JsonMapper.builder().build());
    }

    @Test
    void rateLimitFilterOnlyWatchesPostsToAuthEndpoints() {
        AuthRateLimitFilter f = rateLimit(1);
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/auth/login"))).isTrue();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/members"))).isTrue();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"))).isFalse();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/register"))).isFalse();
        assertThat(f.shouldNotFilter(new MockHttpServletRequest("POST", "/api/v1/auth/register-admin"))).isFalse();
    }

    @Test
    void rateLimitFilterAnswers429ProblemJsonOverTheLimit() throws Exception {
        AuthRateLimitFilter f = rateLimit(1);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockFilterChain first = new MockFilterChain();
        MockHttpServletResponse ok = new MockHttpServletResponse();
        f.doFilter(req, ok, first);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(first.getRequest()).isNotNull();

        MockFilterChain second = new MockFilterChain();
        MockHttpServletResponse limited = new MockHttpServletResponse();
        f.doFilter(req, limited, second);

        assertThat(second.getRequest()).isNull();
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("50");
        assertThat(limited.getContentType()).startsWith("application/problem+json");
        assertThat(limited.getContentAsString()).contains("\"code\":\"TOO_MANY_REQUESTS\"");
    }

}
