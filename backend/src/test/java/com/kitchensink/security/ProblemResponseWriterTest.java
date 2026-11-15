package com.kitchensink.security;




import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

class ProblemResponseWriterTest {

    private final ProblemResponseWriter writer = new ProblemResponseWriter(JsonMapper.builder().build());

    @Test
    void unauthenticatedRequestsGet401WithBearerChallenge() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        writer.commence(new MockHttpServletRequest("GET", "/api/v1/members"), res, new BadCredentialsException("x"));

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(res.getContentType()).startsWith("application/problem+json");
        assertThat(res.getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"").contains("/api/v1/members");
    }

    @Test
    void forbiddenRequestsGet403WithoutChallenge() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        writer.handle(new MockHttpServletRequest("DELETE", "/api/v1/members/1"), res, new AccessDeniedException("no"));

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getHeader("WWW-Authenticate")).isNull();
        assertThat(res.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
