package com.kitchensink.exception.handler;




import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.exception.ErrorCode;
import com.kitchensink.exception.domain.AdminSignupDisabledException;
import com.kitchensink.exception.domain.DuplicateEmailException;
import com.kitchensink.exception.domain.ImportFileException;
import com.kitchensink.exception.domain.InvalidCredentialsException;
import com.kitchensink.exception.domain.InvalidQueryException;
import com.kitchensink.exception.domain.MemberNotFoundException;
import com.kitchensink.exception.domain.VersionConflictException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contract test for the error format, independent of Mongo and of the real controllers. */
class GlobalExceptionHandlerTest {

    @RestController
    static class Boom {
        @GetMapping("/not-found") void notFound() { throw new MemberNotFoundException("42"); }
        @GetMapping("/dup-email") void dup() { throw new DuplicateEmailException(new RuntimeException("E11000 dup key secret-index")); }
        @GetMapping("/version") void version() { throw new VersionConflictException(); }
        @GetMapping("/query") void query() { throw new InvalidQueryException("size", "must be between 1 and 100"); }
        @GetMapping("/admin-off") void adminOff() { throw new AdminSignupDisabledException(); }
        @GetMapping("/bad-creds") void creds() { throw new InvalidCredentialsException(); }
        @GetMapping("/import") void file() { throw new ImportFileException(ErrorCode.UNSUPPORTED_FILE_TYPE, "Only .xlsx files are accepted."); }
        @GetMapping("/bug") void bug() throws IOException { throw new IOException("mongodb://user:pw@host/internal-detail"); }
        @PostMapping("/only-post") void onlyPost() {}
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Boom()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void notFoundIsA404ProblemWithStableCode() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.instance").value("/not-found"))
                .andExpect(jsonPath("$.type").value("https://kitchensink.example/problems/member-not-found"));
    }

    @Test
    void conflictsCarryFieldErrorsAndNeverTheDriverMessage() throws Exception {
        mvc.perform(get("/dup-email"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(content().string(not(containsString("E11000"))))
                .andExpect(content().string(not(containsString("secret-index"))));
        mvc.perform(get("/version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void invalidQueryIsA400WithTheOffendingField() throws Exception {
        mvc.perform(get("/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void authDomainErrorsMapToTheirStatuses() throws Exception {
        mvc.perform(get("/bad-creds")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/admin-off")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/import")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void unexpectedFailuresAreGeneric500sWithoutInternals() throws Exception {
        mvc.perform(get("/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("mongodb://"))))
                .andExpect(content().string(not(containsString("internal-detail"))));
    }

    @Test
    void frameworkErrorsKeepTheirStatusAndHeadersInOurFormat() throws Exception {
        mvc.perform(get("/only-post"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/only-post").contentType("text/plain").content("x"))
                .andExpect(status().isOk());
    }
}
