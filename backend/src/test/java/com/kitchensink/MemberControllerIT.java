package com.kitchensink;




import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** Full-stack tests against a real MongoDB (Testcontainers). Skipped automatically when no Docker daemon is available. Run with `mvn verify`. */
@SpringBootTest(properties = {
        "app.security.jwt-secret=test-only-signing-key-0123456789-abcdef",
        "app.security.admin-signup-code=it-admin-signup-code-1234",
        "app.bootstrap.admin-username=it-admin",
        "app.bootstrap.admin-password=it-admin-password-1",
        "app.bootstrap.viewer-username=it-viewer",
        "app.bootstrap.viewer-password=it-viewer-password-1"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MemberControllerIT {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired MockMvc mvc;

    static String admin;
    static String viewer;

    @BeforeAll
    static void noop() {}

    private String login(String user, String pass) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    private String admin() throws Exception {
        if (admin == null) admin = login("it-admin", "it-admin-password-1");
        return admin;
    }

    private String viewer() throws Exception {
        if (viewer == null) viewer = login("it-viewer", "it-viewer-password-1");
        return viewer;
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String body(String name, String email, String phone) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"phoneNumber\":\"" + phone + "\"}";
    }

    private MvcResult create(String email) throws Exception {
        return mvc.perform(post("/api/v1/members").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Test User", email, "2125551212")))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void healthIsPublicAndMetricsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", admin())).andExpect(status().isOk());
    }

    @Test
    void membersRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void loginRejectsBadCredentialsWithGenericMessage() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"it-admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void createReturns201WithLocationAndNormalizedEmail() throws Exception {
        String email = "Create." + unique() + "@Example.com";
        mvc.perform(post("/api/v1/members").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Ann Lee", email, "2125551212")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/members/")))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void duplicateEmailIsA409WithFieldError() throws Exception {
        String email = "dup." + unique() + "@example.com";
        create(email);
        mvc.perform(post("/api/v1/members").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("Other", email.toUpperCase(), "2125551299")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void invalidBodyIsA400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/members").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body("R2D2", "bad", "12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("phoneNumber")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")));
    }

    @Test
    void unknownFieldsAreRejectedSoClientsCannotSetIds() throws Exception {
        mvc.perform(post("/api/v1/members").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"1\",\"name\":\"A\",\"email\":\"a@b.co\",\"phoneNumber\":\"2125551212\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    private static String userBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void signUpCreatesAViewerWhoCanReadButNotWrite() throws Exception {
        String username = "su-" + unique();
        MvcResult r = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username, "a-long-password-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("VIEWER"))
                .andReturn();
        String token = "Bearer " + JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/members").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/members").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Vi", "v." + unique() + "@example.com", "2125551212")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", token)).andExpect(status().isForbidden());
    }

    @Test
    void signUpCannotChooseARoleAndEnforcesThePasswordPolicy() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sneaky-" + unique()
                                + "\",\"password\":\"a-long-password-1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("short-" + unique(), "tooshort")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void duplicateUsernameIsA409EvenWithDifferentCase() throws Exception {
        String username = "dup-" + unique();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username, "a-long-password-1")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(userBody(username.toUpperCase(), "another-long-password-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void onlyAnAdminCanCreateAdminsAndTheListNeverLeaksHashes() throws Exception {
        String username = "adm-" + unique();
        String payload = "{\"username\":\"" + username + "\",\"password\":\"a-long-password-1\",\"role\":\"ADMIN\"}";

        mvc.perform(post("/api/v1/admin/users").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/users").header("Authorization", viewer())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String newAdmin = login(username, "a-long-password-1");
        mvc.perform(post("/api/v1/members").header("Authorization", newAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Made By Admin", "m." + unique() + "@example.com", "2125551212")))
                .andExpect(status().isCreated());

        String list = mvc.perform(get("/api/v1/admin/users").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(list).contains(username).doesNotContain("passwordHash", "$2a$");
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        mvc.perform(get("/api/v1/members").header("Authorization", viewer())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/members").header("Authorization", viewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Vi", "v." + unique() + "@example.com", "2125551212")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/members/import/template").header("Authorization", viewer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateWithStaleVersionIsA409() throws Exception {
        String email = "upd." + unique() + "@example.com";
        String id = JsonPath.read(create(email).getResponse().getContentAsString(), "$.id");

        mvc.perform(put("/api/v1/members/" + id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\",\"email\":\"" + email + "\",\"phoneNumber\":\"2125551212\",\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Changed"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/v1/members/" + id).header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Late\",\"email\":\"" + email + "\",\"phoneNumber\":\"2125551212\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void deleteThenGetIs404() throws Exception {
        String id = JsonPath.read(create("del." + unique() + "@example.com").getResponse().getContentAsString(), "$.id");
        mvc.perform(delete("/api/v1/members/" + id).header("Authorization", admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/members/" + id).header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(get("/api/v1/members/not-an-object-id").header("Authorization", admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchPagingAndSortingWork() throws Exception {
        String tag = "zz" + unique();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/members").header("Authorization", admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("Name", tag + i + "@example.com", "2125551212")))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/members").header("Authorization", admin())
                        .param("q", tag).param("size", "2").param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].email", startsWith(tag + "2")))
                .andExpect(jsonPath("$.sort").value("email,desc"));
        mvc.perform(get("/api/v1/members").header("Authorization", admin()).param("sort", "passwordHash,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/members").header("Authorization", admin()).param("size", "1000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchTreatsInputAsLiteralTextNotRegex() throws Exception {
        mvc.perform(get("/api/v1/members").header("Authorization", admin()).param("q", ".*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void bulkImportReportsInsertedInvalidAndDuplicateRows() throws Exception {
        String t = unique();
        byte[] file = workbook(new String[][] {
                {"Ok One", "imp1." + t + "@example.com", "2125551001"},
                {"Ok Two", "imp2." + t + "@example.com", "2125551002"},
                {"Bad Row", "not-an-email", "2125551003"},
                {"Dup Row", "IMP1." + t + "@example.com", "2125551004"}});

        mvc.perform(multipart("/api/v1/members/import")
                        .file(new MockMultipartFile("file", "members.xlsx", XLSX, file))
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.invalid").value(1))
                .andExpect(jsonPath("$.duplicatesSkipped").value(1))
                .andExpect(jsonPath("$.errors[?(@.row==4)].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[?(@.row==5)].code", hasItem("DUPLICATE_IN_FILE")));

        // Importing the same file again: everything valid is now a duplicate of an existing member.
        mvc.perform(multipart("/api/v1/members/import")
                        .file(new MockMultipartFile("file", "members.xlsx", XLSX, file))
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.errors[*].code", hasItem("DUPLICATE_EMAIL")));
    }

    @Test
    void importRejectsWrongFileTypeAndBadTemplate() throws Exception {
        mvc.perform(multipart("/api/v1/members/import")
                        .file(new MockMultipartFile("file", "members.csv", "text/csv", "a,b".getBytes()))
                        .header("Authorization", admin()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        mvc.perform(multipart("/api/v1/members/import")
                        .file(new MockMultipartFile("file", "fake.xlsx", XLSX, "not a zip".getBytes()))
                        .header("Authorization", admin()))
                .andExpect(status().isUnsupportedMediaType());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("S").createRow(0).createCell(0).setCellValue("Wrong");
            wb.write(out);
        }
        mvc.perform(multipart("/api/v1/members/import")
                        .file(new MockMultipartFile("file", "headers.xlsx", XLSX, out.toByteArray()))
                        .header("Authorization", admin()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE"));
    }

    @Test
    void templateAndExportDownload() throws Exception {
        mvc.perform(get("/api/v1/members/import/template").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("members-template.xlsx")));
        mvc.perform(get("/api/v1/members/export").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("name,email,phoneNumber,createdAt")));
    }

    private static byte[] workbook(String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Members");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Phone");
            for (int i = 0; i < rows.length; i++) {
                Row r = sheet.createRow(i + 1);
                for (int c = 0; c < 3; c++) {
                    r.createCell(c).setCellValue(rows[i][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void adminSignUpNeedsTheCodeCreatesAnAdminAndDoesNotSignIn() throws Exception {
        String username = "adm-" + unique();
        String good = "{\"username\":\"" + username + "\",\"password\":\"a-long-password-1\","
                + "\"adminCode\":\"it-admin-signup-code-1234\"}";
        String wrongCode = good.replace("it-admin-signup-code-1234", "not-the-code");

        mvc.perform(post("/api/v1/auth/register-admin").contentType(MediaType.APPLICATION_JSON).content(wrongCode))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_CODE"));
        mvc.perform(post("/api/v1/auth/register-admin").contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        // A wrong code is rejected before the username is looked at, so it cannot be used to probe for accounts.
        mvc.perform(post("/api/v1/auth/register-admin").contentType(MediaType.APPLICATION_JSON).content(wrongCode))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/register-admin").contentType(MediaType.APPLICATION_JSON).content(good))
                .andExpect(status().isConflict());

        String token = login(username, "a-long-password-1");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void publicSignUpStillCannotCreateAnAdmin() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x-" + unique() + "\",\"password\":\"a-long-password-1\",\"adminCode\":\"it-admin-signup-code-1234\"}"))
                .andExpect(status().isBadRequest());
    }
}
