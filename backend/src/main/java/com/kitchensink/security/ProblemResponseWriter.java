package com.kitchensink.security;




import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.handler.ApiProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Writes 401/403 from the security filter chain in the same problem format as controller errors. */
@Component
public class ProblemResponseWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ProblemResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        write(res, ApiProblem.of(ErrorCode.UNAUTHENTICATED, "Authentication is required.", req.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        write(res, ApiProblem.of(ErrorCode.FORBIDDEN,
                "You do not have permission to perform this action.", req.getRequestURI()));
    }

    private void write(HttpServletResponse res, ApiProblem pd) throws IOException {
        res.setStatus(pd.status());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (pd.status() == 401) {
            res.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
