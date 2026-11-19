package com.kitchensink.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kitchensink.TestData;
import com.kitchensink.auth.exception.AccountPendingException;
import com.kitchensink.auth.exception.InvalidAdminCodeException;
import com.kitchensink.auth.exception.InvalidCredentialsException;
import com.kitchensink.auth.exception.InvalidPasswordException;
import com.kitchensink.auth.service.AuthService;
import com.kitchensink.auth.dto.ChangePasswordCommand;
import com.kitchensink.auth.dto.LoginCommand;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.auth.dto.RegisterCommand;
import com.kitchensink.member.exception.DuplicateEmailException;
import com.kitchensink.member.model.Role;
import com.kitchensink.security.throttle.TooManyAttemptsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String BODY = """
            {"name":"Asha Rao","email":"asha@example.com","countryCode":"+91","phone":"8123456789",
             "password":"Secret@123","confirmPassword":"Secret@123"%s}""";

    @Mock AuthService authService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = TestData.mockMvc(new AuthController(authService));
    }

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registersAndReturns201() throws Exception {
        when(authService.register(any(RegisterCommand.class))).thenReturn(
                TestData.member("1", Role.VIEWER, "asha@example.com"));

        register(BODY.formatted("")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist());
        verify(authService).register(any(RegisterCommand.class));
    }

    @Test
    void emptyBodyReportsEveryRequiredField() throws Exception {
        register("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='countryCode')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='phone')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='confirmPassword')]").exists());
    }

    @Test
    void mismatchedPasswordsAreReportedOnConfirmPassword() throws Exception {
        register(BODY.formatted("").replace("\"confirmPassword\":\"Secret@123\"", "\"confirmPassword\":\"Other1234\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='confirmPassword')].message").value("Passwords do not match"));
    }

    @Test
    void dummyPhoneIsRejected() throws Exception {
        register(BODY.formatted("").replace("8123456789", "9999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='phone')].message")
                        .value("Enter a valid phone number for the selected country"));
    }

    @Test
    void wrongAdminCodeIsRejected() throws Exception {
        when(authService.register(any(RegisterCommand.class))).thenThrow(new InvalidAdminCodeException());

        register(BODY.formatted(",\"adminCode\":\"WRONG\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ADMIN_CODE"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("adminCode"));
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        when(authService.register(any(RegisterCommand.class))).thenThrow(new DuplicateEmailException());

        register(BODY.formatted("")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    // ---------- login / me / change password ----------

    @Test
    void loginReturnsTheTokenResponse() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenReturn(
                new LoginResult(TestData.member("1", Role.VIEWER, "asha@example.com"), "jwt", 1800));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.member.email").value("asha@example.com"));
    }

    @Test
    void loginNeedsBothFields() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    void aRegistrationWaitingForApprovalIsAnAccountPendingError() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenThrow(new AccountPendingException());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"));
    }

    @Test
    void loginRejectsAbsurdlyLongValues() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + "a".repeat(255) + "\",\"password\":\"" + "p".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')].message").value("Email is too long"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')].message").value("Password is too long"));
        verifyNoInteractions(authService);
    }

    @Test
    void badCredentialsAre401() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void meReturnsThePrincipalsMember() throws Exception {
        when(authService.me("1")).thenReturn(TestData.member("1", Role.ADMIN, "asha@example.com"));

        mvc.perform(get("/api/v1/auth/me").principal(() -> "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private static final String CHANGE = """
            {"currentPassword":"Temp1234","newPassword":"Secret@123","confirmPassword":"%s"}""";

    @Test
    void changePasswordReturns204() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE.formatted("Secret@123")))
                .andExpect(status().isNoContent());
        verify(authService).changePassword(eq("1"), any(ChangePasswordCommand.class));
    }

    @Test
    void changePasswordValidatesTheBody() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE.formatted("Other1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='confirmPassword')].message").value("Passwords do not match"));
    }

    @Test
    void wrongCurrentPasswordIs400OnThatField() throws Exception {
        doThrow(new InvalidPasswordException("currentPassword", "Current password is incorrect"))
                .when(authService).changePassword(eq("1"), any(ChangePasswordCommand.class));

        mvc.perform(post("/api/v1/auth/change-password").principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON).content(CHANGE.formatted("Secret@123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
    }

    // ---------- client address, logout, lockout ----------

    @Test
    void theClientAddressReachesTheServiceOnLoginAndRegister() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenReturn(
                new LoginResult(TestData.member("1", Role.VIEWER, "asha@example.com"), "jwt", 1800));
        when(authService.register(any(RegisterCommand.class))).thenReturn(
                TestData.member("1", Role.VIEWER, "asha@example.com"));

        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@example.com\",\"password\":\"x\"}"));
        mvc.perform(post("/api/v1/auth/register").with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("")));

        ArgumentCaptor<LoginCommand> login = ArgumentCaptor.forClass(LoginCommand.class);
        verify(authService).login(login.capture());
        assertThat(login.getValue().clientIp()).isEqualTo("203.0.113.9");
        ArgumentCaptor<RegisterCommand> register = ArgumentCaptor.forClass(RegisterCommand.class);
        verify(authService).register(register.capture());
        assertThat(register.getValue().clientIp()).isEqualTo("203.0.113.9");
    }

    @Test
    void aLockoutIs429WithRetryAfter() throws Exception {
        when(authService.login(any(LoginCommand.class))).thenThrow(new TooManyAttemptsException(900));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void logoutReturns204ForThePrincipal() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").principal(() -> "1")).andExpect(status().isNoContent());
        verify(authService).logout("1");
    }
}
