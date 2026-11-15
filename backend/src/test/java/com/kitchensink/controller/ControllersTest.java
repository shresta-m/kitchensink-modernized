package com.kitchensink.controller;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kitchensink.dto.auth.AdminRegisterRequest;
import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.dto.auth.LoginRequest;
import com.kitchensink.dto.auth.LoginResponse;
import com.kitchensink.dto.auth.RegisterRequest;
import com.kitchensink.dto.common.PageResponse;
import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.dto.member.BulkDeleteRequest;
import com.kitchensink.dto.member.BulkDeleteResponse;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberResponse;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortDirection;
import com.kitchensink.dto.member.UpdateMemberRequest;
import com.kitchensink.dto.user.CreateUserRequest;
import com.kitchensink.dto.user.UserResponse;
import com.kitchensink.exception.domain.ImportFileException;
import com.kitchensink.model.Member;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import com.kitchensink.service.AuthService;
import com.kitchensink.service.MemberExportService;
import com.kitchensink.service.MemberImportService;
import com.kitchensink.service.MemberService;
import com.kitchensink.service.UserService;
import com.kitchensink.validation.XlsxUploadGuard;
import java.io.InputStream;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Controllers are thin, so they are tested directly against mocked services: no web server, no database. */
class ControllersTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @BeforeEach
    void requestContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", "/api/v1/members")));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Member member(String id) {
        Member m = Member.register("Ann", "ann@example.com", "2125551212", NOW);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    // ---- AuthController -----------------------------------------------------------------------

    @Test
    void authControllerMapsServiceResultsToResponses() {
        AuthService auth = mock(AuthService.class);
        AuthToken token = new AuthToken("jwt", 1800, "jane", Role.VIEWER);
        when(auth.login("jane", "pw-pw-pw-pw-1")).thenReturn(token);
        when(auth.register("jane", "pw-pw-pw-pw-1")).thenReturn(token);
        when(auth.registerAdmin("boss", "pw-pw-pw-pw-1", "code")).thenReturn(new User("boss", "h", Role.ADMIN, NOW));
        AuthController controller = new AuthController(auth);

        assertThat(controller.login(new LoginRequest("jane", "pw-pw-pw-pw-1")).user().username()).isEqualTo("jane");

        ResponseEntity<LoginResponse> registered = controller.register(new RegisterRequest("jane", "pw-pw-pw-pw-1"));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().accessToken()).isEqualTo("jwt");

        ResponseEntity<UserResponse> admin = controller.registerAdmin(new AdminRegisterRequest("boss", "pw-pw-pw-pw-1", "code"));
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(admin.getBody().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void meReturnsSubjectAndFirstRoleOrBlank() {
        AuthController controller = new AuthController(mock(AuthService.class));
        Jwt withRole = Jwt.withTokenValue("t").header("alg", "HS256").subject("jane").claim("roles", List.of("ADMIN")).build();
        Jwt emptyRoles = Jwt.withTokenValue("t").header("alg", "HS256").subject("jane").claim("roles", List.of()).build();
        Jwt noRoles = Jwt.withTokenValue("t").header("alg", "HS256").subject("jane").claim("other", "x").build();

        assertThat(controller.me(withRole)).isEqualTo(new LoginResponse.UserInfo("jane", "ADMIN"));
        assertThat(controller.me(emptyRoles).role()).isEmpty();
        assertThat(controller.me(noRoles).role()).isEmpty();
    }

    // ---- MemberController ---------------------------------------------------------------------

    @Test
    void listParsesSortAndBuildsThePageResponse() {
        MemberService service = mock(MemberService.class);
        when(service.search(eq("an"), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(member("1")), 1, 5, 11));

        PageResponse<MemberResponse> page = new MemberController(service).list("an", 1, 5, "email,desc");

        ArgumentCaptor<PageQuery> query = ArgumentCaptor.forClass(PageQuery.class);
        verify(service).search(eq("an"), query.capture());
        assertThat(query.getValue().page()).isEqualTo(1);
        assertThat(query.getValue().sort().direction()).isEqualTo(SortDirection.DESC);
        assertThat(page.sort()).isEqualTo("email,desc");
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.content().get(0).id()).isEqualTo("1");
    }

    @Test
    void createReturns201WithALocationHeader() {
        MemberService service = mock(MemberService.class);
        MemberRequest body = new MemberRequest("Ann", "ann@example.com", "2125551212");
        when(service.create(body)).thenReturn(member("abc"));

        ResponseEntity<MemberResponse> response = new MemberController(service).create(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("http://localhost/api/v1/members/abc");
        assertThat(response.getBody().email()).isEqualTo("ann@example.com");
    }

    @Test
    void getUpdateDeleteAndBulkDeleteDelegate() {
        MemberService service = mock(MemberService.class);
        UpdateMemberRequest update = new UpdateMemberRequest("Ann", "ann@example.com", "2125551212", 0L);
        when(service.get("1")).thenReturn(member("1"));
        when(service.update("1", update)).thenReturn(member("1"));
        BulkDeleteResponse result = new BulkDeleteResponse(2, 1, List.of("2"));
        when(service.bulkDelete(List.of("1", "2"))).thenReturn(result);
        MemberController controller = new MemberController(service);

        assertThat(controller.get("1").id()).isEqualTo("1");
        assertThat(controller.update("1", update).id()).isEqualTo("1");
        assertThat(controller.delete("1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete("1");
        assertThat(controller.bulkDelete(new BulkDeleteRequest(List.of("1", "2")))).isSameAs(result);
    }

    // ---- MemberExportController ---------------------------------------------------------------

    @Test
    void exportStreamsCsvWithDownloadHeaders() throws Exception {
        MemberExportService exportService = mock(MemberExportService.class);
        org.mockito.Mockito.doAnswer(inv -> {
            Writer out = inv.getArgument(2);
            out.write("name\r\n");
            return null;
        }).when(exportService).exportCsv(eq("q"), any(), any(Writer.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MemberExportController(exportService).export("q", "name,desc", response);

        assertThat(response.getContentType()).startsWith("text/csv");
        assertThat(response.getHeader("Content-Disposition")).contains("members.csv");
        assertThat(response.getContentAsString()).isEqualTo("name\r\n");
    }

    @Test
    void exportRejectsABadSortBeforeWritingAnything() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new MemberExportController(mock(MemberExportService.class))
                .export(null, "password", response)).isInstanceOf(com.kitchensink.exception.domain.InvalidQueryException.class);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

    // ---- MemberImportController ---------------------------------------------------------------

    @Test
    void uploadValidatesTheFileThenReturnsTheReport() throws Exception {
        MemberImportService service = mock(MemberImportService.class);
        when(service.importMembers(any(InputStream.class))).thenReturn(new ImportReport(1, 1, 0, 0, 0, List.of(), false));
        MockMultipartFile file = new MockMultipartFile("file", "m.xlsx", "application/octet-stream", new byte[] {'P', 'K', 3, 4});

        var report = new MemberImportController(service, new XlsxUploadGuard()).upload(file);

        assertThat(report.inserted()).isEqualTo(1);
    }

    @Test
    void uploadRejectsNonXlsxFilesWithoutCallingTheService() {
        MemberImportService service = mock(MemberImportService.class);
        MockMultipartFile file = new MockMultipartFile("file", "m.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> new MemberImportController(service, new XlsxUploadGuard()).upload(file))
                .isInstanceOf(ImportFileException.class);
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void templateIsServedAsAnXlsxAttachment() {
        MemberImportService service = mock(MemberImportService.class);
        when(service.template()).thenReturn(new byte[] {1, 2, 3});

        ResponseEntity<byte[]> response = new MemberImportController(service, new XlsxUploadGuard()).template();

        assertThat(response.getBody()).containsExactly(1, 2, 3);
        assertThat(response.getHeaders().getContentType()).hasToString(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("members-template.xlsx");
    }

    // ---- UserAdminController ------------------------------------------------------------------

    @Test
    void adminCanCreateAndListUsersWithoutHashesInTheResponse() {
        UserService users = mock(UserService.class);
        User jane = new User("jane", "secret-hash", Role.VIEWER, NOW);
        when(users.create("jane", "pw-pw-pw-pw-1", Role.VIEWER)).thenReturn(jane);
        when(users.list()).thenReturn(List.of(jane));
        UserAdminController controller = new UserAdminController(users);

        ResponseEntity<UserResponse> created = controller.create(new CreateUserRequest("jane", "pw-pw-pw-pw-1", Role.VIEWER));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().username()).isEqualTo("jane");
        assertThat(controller.list()).extracting(UserResponse::username).containsExactly("jane");
    }
}
