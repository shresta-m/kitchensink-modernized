package com.kitchensink.member.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.TestData;
import com.kitchensink.member.exception.*;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.service.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final String BASE = "/api/v1/members";
    private static final String PROFILE = """
            "name":"Asha Rao","email":"asha@example.com","countryCode":"+91","phone":"8123456789\"""";

    @Mock
    MemberService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestData.mockMvc(new MemberController(service));
    }

    private static Member response(Role role) {
        return TestData.member("1", role, "asha@example.com");
    }

    private static Page<Member> page() {
        return new PageImpl<>(List.of(response(Role.VIEWER)), PageRequest.of(0, 8), 1);
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               String body) throws Exception {
        return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ---------- list ----------

    @Test
    void listUsesDefaults() throws Exception {
        when(service.list(new MemberSearchCriteria(null, null, null), "name,asc", 0, 8)).thenReturn(page());

        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("asha@example.com"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listPassesFiltersSortAndPaging() throws Exception {
        when(service.list(new MemberSearchCriteria("asha", Role.ADMIN, MemberStatus.TEMP_PASSWORD),
                "email,desc", 2, 20)).thenReturn(page());

        mvc.perform(get(BASE).param("search", "asha").param("role", "ADMIN").param("status", "TEMP_PASSWORD")
                        .param("sort", "email,desc").param("page", "2").param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void listRejectsNegativePage() throws Exception {
        mvc.perform(get(BASE).param("page", "-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("page must be 0 or more"));
    }

    @Test
    void listRejectsSizeOutsideOneToHundred() throws Exception {
        mvc.perform(get(BASE).param("size", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("size must be between 1 and 100"));
        mvc.perform(get(BASE).param("size", "101")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("size must be between 1 and 100"));
    }

    @Test
    void listRejectsOverlongSearch() throws Exception {
        mvc.perform(get(BASE).param("search", "a".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("search must be at most 100 characters"));
    }

    @Test
    void listRejectsUnknownRole() throws Exception {
        mvc.perform(get(BASE).param("role", "BOSS")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'role'"));
    }

    // ---------- export ----------

    @Test
    void exportReturnsCsvDownload() throws Exception {
        byte[] bytes = "Name\r\n".getBytes(StandardCharsets.UTF_8);
        when(service.exportCsv(new MemberSearchCriteria("a", Role.VIEWER, null), "name,asc"))
                .thenReturn(new CsvExport("members-2026-11-17.csv", bytes));

        mvc.perform(get(BASE + "/export").param("search", "a").param("role", "VIEWER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("members-2026-11-17.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void exportRejectsOverlongSearch() throws Exception {
        mvc.perform(get(BASE + "/export").param("search", "a".repeat(101))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("search must be at most 100 characters"));
    }

    // ---------- get ----------

    @Test
    void getReturnsMember() throws Exception {
        when(service.get("1")).thenReturn(response(Role.VIEWER));

        mvc.perform(get(BASE + "/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void getUnknownMemberIs404() throws Exception {
        when(service.get("9")).thenThrow(new MemberNotFoundException("9"));

        mvc.perform(get(BASE + "/9")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    // ---------- create ----------

    @Test
    void createReturns201WithTemporaryPasswordAndNoStore() throws Exception {
        when(service.create(any(CreateMemberCommand.class)))
                .thenReturn(new CreatedMember(response(Role.ADMIN), "Temp1234"));

        json(post(BASE), "{" + PROFILE + ",\"role\":\"ADMIN\"}").andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp1234"))
                .andExpect(jsonPath("$.member.role").value("ADMIN"));
    }

    @Test
    void createRequiresARole() throws Exception {
        json(post(BASE), "{" + PROFILE + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='role')].message").value("Choose a role"));
    }

    @Test
    void createWithTakenEmailIs409() throws Exception {
        when(service.create(any(CreateMemberCommand.class))).thenThrow(new DuplicateEmailException());

        json(post(BASE), "{" + PROFILE + ",\"role\":\"VIEWER\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    // ---------- approve ----------

    @Test
    void approveReturnsTheApprovedMember() throws Exception {
        when(service.approve("1")).thenReturn(response(Role.VIEWER));

        mvc.perform(post(BASE + "/1/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1")).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void approvingAMemberWhoIsNotWaitingIs409() throws Exception {
        when(service.approve("1")).thenThrow(new NotPendingApprovalException("1"));

        mvc.perform(post(BASE + "/1/approve")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_PENDING_APPROVAL"));
    }

    // ---------- reset password ----------

    @Test
    void resetPasswordReturnsTemporaryPasswordWithNoStore() throws Exception {
        when(service.resetPassword("1")).thenReturn("Temp5678");

        mvc.perform(post(BASE + "/1/reset-password")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp5678"));
    }

    // ---------- update / role ----------

    @Test
    void updateChangesProfile() throws Exception {
        when(service.update(eq("1"), any(UpdateMemberCommand.class))).thenReturn(response(Role.VIEWER));

        json(put(BASE + "/1"), "{" + PROFILE + ",\"version\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.com"));
    }

    @Test
    void updateValidatesProfile() throws Exception {
        json(put(BASE + "/1"), "{\"name\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='version')].message").value("Version is required"));
    }

    @Test
    void changeRoleReturnsUpdatedMember() throws Exception {
        when(service.changeRole(eq("1"), any(ChangeRoleCommand.class))).thenReturn(response(Role.ADMIN));

        json(put(BASE + "/1/role"), "{\"role\":\"ADMIN\",\"version\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void changeRoleNeedsARole() throws Exception {
        json(put(BASE + "/1/role"), "{\"version\":0}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Choose a role"));
    }

    @Test
    void demotingTheLastAdminIs409() throws Exception {
        when(service.changeRole(eq("1"), any(ChangeRoleCommand.class))).thenThrow(new LastAdminException());

        json(put(BASE + "/1/role"), "{\"role\":\"VIEWER\",\"version\":0}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    void staleVersionIs409() throws Exception {
        when(service.update(eq("1"), any(UpdateMemberCommand.class))).thenThrow(new StaleVersionException());

        json(put(BASE + "/1"), "{" + PROFILE + ",\"version\":0}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("version"));
    }

    // ---------- delete ----------

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete(BASE + "/1")).andExpect(status().isNoContent());
        verify(service).delete("1");
    }

    @Test
    void deletingAnAdministratorIs409() throws Exception {
        doThrow(new AdminDeletionException()).when(service).delete("2");

        mvc.perform(delete(BASE + "/2")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_MUST_BE_DEMOTED"));
    }

    @Test
    void pageBeyondTheOffsetCapIs400() throws Exception {
        when(service.list(new MemberSearchCriteria(null, null, null), "name,asc", 101, 100))
                .thenThrow(new PageTooDeepException(10_000));

        mvc.perform(get(BASE).param("page", "101").param("size", "100")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_TOO_DEEP"));
    }

    @Test
    void bulkDeleteReturnsCount() throws Exception {
        when(service.bulkDelete(List.of("a", "b"))).thenReturn(2);

        json(post(BASE + "/bulk-delete"), "{\"ids\":[\"a\",\"b\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(2));
    }

    @Test
    void bulkDeleteNeedsAtLeastOneId() throws Exception {
        json(post(BASE + "/bulk-delete"), "{\"ids\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Select at least one member"));
    }

    // ---------- generic error handling over HTTP ----------

    @Test
    void malformedJsonIs400() throws Exception {
        json(post(BASE), "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsupportedMethodKeepsItsStatus() throws Exception {
        mvc.perform(patch(BASE + "/1")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
    }

    @Test
    void unsupportedMediaTypeKeepsItsStatus() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unexpectedFailureIs500WithoutInternals() throws Exception {
        when(service.get("1")).thenThrow(new IllegalStateException("mongo password is hunter2"));

        mvc.perform(get(BASE + "/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong. Try again later."));
    }
}
