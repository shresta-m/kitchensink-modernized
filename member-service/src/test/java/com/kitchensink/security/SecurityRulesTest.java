package com.kitchensink.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.TestData;
import com.kitchensink.auth.controller.AuthController;
import com.kitchensink.auth.service.AuthService;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.common.config.AppProperties;
import com.kitchensink.common.exception.GlobalExceptionHandler;
import com.kitchensink.lookup.controller.LookupController;
import com.kitchensink.member.controller.MemberController;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import com.kitchensink.member.service.CreatedMember;
import com.kitchensink.member.service.CsvExport;
import com.kitchensink.member.service.MemberService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The real security filter chain, controllers and error handler, with the services and the repository mocked.
 * Proves who can call what, and that every rejection has the normal JSON error shape.
 */
@SpringJUnitWebConfig(SecurityRulesTest.TestConfig.class)
class SecurityRulesTest {

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, MemberJwtAuthenticationConverter.class, JwtTokenService.class,
            MemberController.class, AuthController.class, LookupController.class, GlobalExceptionHandler.class})
    static class TestConfig {
        @Bean AppProperties appProperties() { return TestData.props("CODE"); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean MemberService memberService() { return mock(MemberService.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean MemberRepository memberRepository() { return mock(MemberRepository.class); }
    }

    private static final String PROFILE = "\"name\":\"Asha Rao\",\"email\":\"asha@example.com\","
            + "\"countryCode\":\"+91\",\"phone\":\"8123456789\"";

    @Autowired WebApplicationContext context;
    @Autowired MemberRepository repository;
    @Autowired MemberService memberService;
    @Autowired AuthService authService;
    @Autowired JwtTokenService tokens;
    @Autowired AppProperties props;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(repository, memberService, authService);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------- helpers ----------

    private String tokenFor(String id, Role role, boolean temporaryPassword) {
        Member member = temporaryPassword
                ? Member.createByAdmin(TestData.details(id + "@example.com"), role, "h", TestData.NOW)
                : Member.register(TestData.details(id + "@example.com"), role, "h", false, TestData.NOW);
        ReflectionTestUtils.setField(member, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(member));
        return tokens.issue(member).value();
    }

    private void stubMemberService() {
        Member member = TestData.member("1", Role.VIEWER, "a@example.com");
        when(memberService.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 8), 1));
        when(memberService.exportCsv(any(), any())).thenReturn(new CsvExport("members.csv", new byte[] {1}));
        when(memberService.get("1")).thenReturn(member);
        when(memberService.create(any())).thenReturn(new CreatedMember(member, "Temp1234"));
        when(memberService.update(eq("1"), any())).thenReturn(member);
        when(memberService.changeRole(eq("1"), any())).thenReturn(member);
        when(memberService.resetPassword("1")).thenReturn("Temp5678");
        when(memberService.approve("1")).thenReturn(member);
    }

    private String admin() { return tokenFor("admin", Role.ADMIN, false); }

    private String viewer() { return tokenFor("viewer", Role.VIEWER, false); }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions callWithBody(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return call(request.content(body), token);
    }

    // ---------- public endpoints ----------

    @Test
    void registerLoginAndApiDocsNeedNoToken() throws Exception {
        callWithBody(post("/api/v1/auth/register"), null, "{}").andExpect(status().isBadRequest());   // reaches validation
        callWithBody(post("/api/v1/auth/login"), null, "{}").andExpect(status().isBadRequest());
        call(get("/v3/api-docs"), null).andExpect(status().isNotFound());                              // not 401: it is public

        when(authService.login(any())).thenReturn(
                new LoginResult(TestData.member("1", Role.VIEWER, "a@example.com"), "t", 1800));
        callWithBody(post("/api/v1/auth/login"), null, "{\"email\":\"a@example.com\",\"password\":\"x\"}")
                .andExpect(status().isOk());
    }

    // ---------- not signed in / bad tokens ----------

    @Test
    void missingTokenIs401WithTheStandardErrorShape() throws Exception {
        call(get("/api/v1/members"), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.path").value("/api/v1/members"));
        call(get("/api/v1/auth/me"), null).andExpect(status().isUnauthorized());
        callWithBody(post("/api/v1/members"), null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    void garbageAndTamperedTokensAre401() throws Exception {
        call(get("/api/v1/members"), "not-a-jwt").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        String token = admin();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        call(get("/api/v1/members"), tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIs401() throws Exception {
        Member member = Member.register(TestData.details("old@example.com"), Role.ADMIN, "h", false, TestData.NOW);
        ReflectionTestUtils.setField(member, "id", "old");
        when(repository.findById("old")).thenReturn(Optional.of(member));
        Instant longAgo = Instant.now().minus(Duration.ofHours(2));
        JwtTokenService past = new JwtTokenService(new SecurityConfig().jwtEncoder(props), props,
                Clock.fixed(longAgo, ZoneOffset.UTC));

        call(get("/api/v1/members"), past.issue(member).value()).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenOfADeletedMemberIs401() throws Exception {
        String token = admin();
        when(repository.findById("admin")).thenReturn(Optional.empty());

        call(get("/api/v1/members"), token).andExpect(status().isUnauthorized());
    }

    // ---------- viewer: read and search only ----------

    @Test
    void viewerCanListSearchAndViewButNotExport() throws Exception {
        String token = viewer();
        stubMemberService();

        call(get("/api/v1/members?search=asha&sort=name,asc"), token).andExpect(status().isOk());
        call(get("/api/v1/members/1"), token).andExpect(status().isOk());
        // the export is the whole member list with phone numbers in one file: ADMIN only, not just a read
        call(get("/api/v1/members/export"), token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void viewerCannotChangeAnything() throws Exception {
        String token = viewer();

        callWithBody(post("/api/v1/members"), token, "{" + PROFILE + ",\"role\":\"VIEWER\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        callWithBody(put("/api/v1/members/1"), token, "{" + PROFILE + ",\"version\":0}").andExpect(status().isForbidden());
        callWithBody(put("/api/v1/members/1/role"), token, "{\"role\":\"ADMIN\",\"version\":0}").andExpect(status().isForbidden());
        call(post("/api/v1/members/1/reset-password"), token).andExpect(status().isForbidden());
        call(post("/api/v1/members/1/approve"), token).andExpect(status().isForbidden());     // a viewer cannot approve anyone
        call(delete("/api/v1/members/1"), token).andExpect(status().isForbidden());
        callWithBody(post("/api/v1/members/bulk-delete"), token, "{\"ids\":[\"1\"]}").andExpect(status().isForbidden());
    }

    // ---------- admin: everything ----------

    @Test
    void adminCanDoEverything() throws Exception {
        String token = admin();
        stubMemberService();

        call(get("/api/v1/members"), token).andExpect(status().isOk());
        call(get("/api/v1/members/export"), token).andExpect(status().isOk());
        call(get("/api/v1/members/1"), token).andExpect(status().isOk());
        callWithBody(post("/api/v1/members"), token, "{" + PROFILE + ",\"role\":\"VIEWER\"}").andExpect(status().isCreated());
        callWithBody(put("/api/v1/members/1"), token, "{" + PROFILE + ",\"version\":0}").andExpect(status().isOk());
        callWithBody(put("/api/v1/members/1/role"), token, "{\"role\":\"ADMIN\",\"version\":0}").andExpect(status().isOk());
        call(post("/api/v1/members/1/reset-password"), token).andExpect(status().isOk());
        call(post("/api/v1/members/1/approve"), token).andExpect(status().isOk());
        call(delete("/api/v1/members/1"), token).andExpect(status().isNoContent());
        callWithBody(post("/api/v1/members/bulk-delete"), token, "{\"ids\":[\"1\"]}").andExpect(status().isOk());
    }

    @Test
    void lookupsAreReadableByAdminsAndViewersButNotAnonymous() throws Exception {
        call(get("/api/v1/lookups/roles"), null).andExpect(status().isUnauthorized());
        call(get("/api/v1/lookups/roles"), viewer()).andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value("ADMIN"));
        call(get("/api/v1/lookups/statuses"), admin()).andExpect(status().isOk()).andExpect(jsonPath("$[1].value").value("TEMP_PASSWORD"));
        call(get("/api/v1/lookups/roles"), tokenFor("temp", Role.ADMIN, true)).andExpect(status().isForbidden());
    }

    @Test
    void healthProbesArePublicAndEverythingElseUnderActuatorIsNot() throws Exception {
        call(get("/actuator/health"), null).andExpect(status().isNotFound());              // permitted; no endpoint in this slice
        call(get("/actuator/health/liveness"), null).andExpect(status().isNotFound());
        call(get("/actuator/env"), null).andExpect(status().isUnauthorized());
        call(get("/actuator/env"), admin()).andExpect(status().isForbidden());
    }

    @Test
    void unknownPathsAreDeniedEvenForAdmins() throws Exception {
        call(get("/api/v1/unknown"), admin()).andExpect(status().isForbidden());
    }

    // ---------- sessions: sign-out, revocation, headers ----------

    @Test
    void anySignedInMemberCanSignOut_evenWithATemporaryPassword() throws Exception {
        call(post("/api/v1/auth/logout"), viewer()).andExpect(status().isNoContent());
        call(post("/api/v1/auth/logout"), tokenFor("temp", Role.VIEWER, true)).andExpect(status().isNoContent());
        call(post("/api/v1/auth/logout"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenIsDeadOnceTheMembersSessionsAreRevoked() throws Exception {
        String token = admin();
        stubMemberService();
        call(get("/api/v1/members"), token).andExpect(status().isOk());

        Member current = repository.findById("admin").orElseThrow();
        current.revokeSessions(TestData.NOW);                      // what sign-out and a password change do

        call(get("/api/v1/members"), token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        call(get("/api/v1/auth/me"), token).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesCarryTheSecurityHeaders() throws Exception {
        mvc.perform(get("/api/v1/members").secure(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY))
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    // ---------- own account ----------

    @Test
    void anySignedInMemberCanReadAndChangeTheirOwnAccount() throws Exception {
        String token = viewer();
        when(authService.me("viewer")).thenReturn(
                TestData.member("viewer", Role.VIEWER, "viewer@example.com"));

        call(get("/api/v1/auth/me"), token).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("viewer"));
        callWithBody(post("/api/v1/auth/change-password"), token,
                "{\"currentPassword\":\"Old12345\",\"newPassword\":\"Secret@123\",\"confirmPassword\":\"Secret@123\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void memberWithATemporaryPasswordCanOnlyChangeIt() throws Exception {
        String token = tokenFor("temp", Role.ADMIN, true);      // even an admin is locked out until the password is changed
        when(authService.me("temp")).thenReturn(
                TestData.member("temp", Role.ADMIN, "temp@example.com"));

        call(get("/api/v1/members"), token).andExpect(status().isForbidden());
        callWithBody(post("/api/v1/members"), token, "{" + PROFILE + ",\"role\":\"VIEWER\"}").andExpect(status().isForbidden());
        call(get("/api/v1/auth/me"), token).andExpect(status().isOk());
        callWithBody(post("/api/v1/auth/change-password"), token,
                "{\"currentPassword\":\"Temp1234\",\"newPassword\":\"Secret@123\",\"confirmPassword\":\"Secret@123\"}")
                .andExpect(status().isNoContent());
    }

    // ---------- CORS ----------

    @Test
    void preflightIsAllowedOnlyForTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/members").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mvc.perform(options("/api/v1/members").header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
