package com.kitchensink.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.auth.exception.AccountPendingException;
import com.kitchensink.auth.exception.InvalidAdminCodeException;
import com.kitchensink.auth.exception.InvalidCredentialsException;
import com.kitchensink.auth.exception.InvalidPasswordException;
import com.kitchensink.auth.dto.ChangePasswordCommand;
import com.kitchensink.auth.dto.LoginCommand;
import com.kitchensink.auth.dto.LoginResult;
import com.kitchensink.common.exception.ErrorCode;
import com.kitchensink.member.exception.MemberNotFoundException;
import com.kitchensink.common.config.AppConfig;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.repository.MemberRepository;
import com.kitchensink.member.service.MemberCreator;
import com.kitchensink.security.JwtTokenService;
import com.kitchensink.security.throttle.AuthThrottle;
import com.kitchensink.security.throttle.TooManyAttemptsException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock MemberCreator creator;
    @Mock MemberRepository repository;
    @Mock JwtTokenService tokens;
    @Mock PasswordEncoder encoder;
    @Mock AuthThrottle throttle;

    private AuthServiceImpl service(String configuredCode) {
        return new AuthServiceImpl(creator, repository, tokens, encoder, TestData.props(configuredCode), TestData.CLOCK,
                throttle);
    }

    private void stubSave() {
        when(encoder.encode("Secret@123")).thenReturn("encoded");
        when(creator.create(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void withoutCodeRegistersViewer(String code) {
        stubSave();

        Member response = service("SECRET-CODE").register(TestData.registerCommand(code));

        assertThat(response.getRole()).isEqualTo(Role.VIEWER);
        assertThat(response.isPendingApproval()).isTrue();             // nobody vouched for this registration
        assertThat(response.getEmail()).isEqualTo("asha@example.com");
        assertThat(response.getName()).isEqualTo("Asha Rao");
    }

    @Test
    void storesEncodedPasswordAndNormalizedFields() {
        stubSave();

        service("SECRET-CODE").register(TestData.registerCommand(null));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(creator).create(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(saved.getValue().isMustChangePassword()).isFalse();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void matchingCodeRegistersAdmin() {
        stubSave();

        Member admin = service("SECRET-CODE").register(TestData.registerCommand(" SECRET-CODE "));

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isPendingApproval()).isFalse();               // the admin code vouches for the registrant
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void anyCodeIsRejectedWhenNoneIsConfigured(String configured) {
        assertThatThrownBy(() -> service(configured).register(TestData.registerCommand("SECRET-CODE")))
                .isInstanceOf(InvalidAdminCodeException.class);
        verifyNoInteractions(creator);
    }

    @Test
    void wrongCodeIsRejectedNotDowngraded() {
        assertThatThrownBy(() -> service("SECRET-CODE").register(TestData.registerCommand("WRONG")))
                .isInstanceOfSatisfying(InvalidAdminCodeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_ADMIN_CODE);
                    assertThat(e.getField()).isEqualTo("adminCode");
                });
        verify(creator, never()).create(any(Member.class));
    }

    // ---------- login ----------

    private static LoginCommand login(String email, String password) {
        return new LoginCommand(email, password, TestData.IP);
    }

    @Test
    void loginIssuesATokenForValidCredentials() {
        Member member = Member.createByAdmin(TestData.details("asha@example.com"), Role.VIEWER, "hash", TestData.NOW);
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(member));
        when(encoder.matches("Secret@123", "hash")).thenReturn(true);
        when(tokens.issue(member)).thenReturn(new JwtTokenService.IssuedToken("jwt", 1800));

        LoginResult result = service("X").login(login(" ASHA@Example.com ", "Secret@123"));

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.expiresInSeconds()).isEqualTo(1800);
        assertThat(result.member().isMustChangePassword()).isTrue();
        assertThat(result.member().getEmail()).isEqualTo("asha@example.com");
    }

    @Test
    void loginWithTheRightPasswordIsRefusedWhileTheRegistrationIsPending() {
        Member pending = Member.register(TestData.details("asha@example.com"), Role.VIEWER, "hash", true, TestData.NOW);
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(pending));
        when(encoder.matches("Secret@123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service("X").login(login("asha@example.com", "Secret@123")))
                .isInstanceOf(AccountPendingException.class);
        verifyNoInteractions(tokens);                                  // no token for an unapproved member
        verify(throttle, never()).loginFailed(any(), any());           // the credentials were right: not a failed attempt
    }

    @Test
    void loginWithAWrongPasswordOnAPendingAccountLooksLikeAnyOtherWrongPassword() {
        Member pending = Member.register(TestData.details("asha@example.com"), Role.VIEWER, "hash", true, TestData.NOW);
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(pending));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service("X").login(login("asha@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);      // pending is only revealed to someone with the password
    }

    @Test
    void loginWithAnUnknownEmailStillChecksAHashAndFailsTheSameWay() {
        when(repository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("X").login(login("nobody@example.com", "Secret@123")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Invalid email or password");
        verify(encoder).matches(eq("Secret@123"), startsWith("$2a$" + AppConfig.BCRYPT_STRENGTH + "$"));
        verifyNoInteractions(tokens);
    }

    @Test
    void loginWithAWrongPasswordFailsTheSameWay() {
        Member member = TestData.member("1", Role.VIEWER, "asha@example.com");
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(member));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service("X").login(login("asha@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(tokens);
    }

    // ---------- me / change password ----------

    private static ChangePasswordCommand change(String current, String next) {
        return new ChangePasswordCommand(current, next);
    }

    @Test
    void meReturnsTheMemberOrFails() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.VIEWER, "asha@example.com")));
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThat(service("X").me("1").getId()).isEqualTo("1");
        assertThatThrownBy(() -> service("X").me("x")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void changePasswordStoresTheNewHashAndClearsTheTemporaryFlag() {
        Member member = Member.createByAdmin(TestData.details("asha@example.com"), Role.VIEWER, "temp-hash", TestData.NOW);
        when(repository.findById("1")).thenReturn(Optional.of(member));
        when(encoder.matches("Temp1234", "temp-hash")).thenReturn(true);
        when(encoder.matches("Secret@123", "temp-hash")).thenReturn(false);
        when(encoder.encode("Secret@123")).thenReturn("new-hash");

        service("X").changePassword("1", change("Temp1234", "Secret@123"));

        assertThat(member.getPasswordHash()).isEqualTo("new-hash");
        assertThat(member.isMustChangePassword()).isFalse();
        verify(repository).save(member);
    }

    @Test
    void changePasswordRejectsAWrongCurrentPassword() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.VIEWER, "asha@example.com")));
        when(encoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service("X").changePassword("1", change("bad", "Secret@123")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("currentPassword"));
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void changePasswordRejectsReusingTheCurrentPassword() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.VIEWER, "asha@example.com")));
        when(encoder.matches("Secret@123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service("X").changePassword("1", change("Secret@123", "Secret@123")))
                .isInstanceOfSatisfying(InvalidPasswordException.class,
                        e -> assertThat(e.getField()).isEqualTo("newPassword"));
        verify(repository, never()).save(any(Member.class));
    }

    @Test
    void changePasswordFailsForAMissingMember() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("X").changePassword("x", change("a", "Secret@123")))
                .isInstanceOf(MemberNotFoundException.class);
    }

    // ---------- brute-force throttling ----------

    @Test
    void registrationAssignsTheRequestedRole() {
        stubSave();

        Member saved = service("SECRET-CODE").register(TestData.registerCommand(null));

        assertThat(saved.getRole()).isEqualTo(Role.VIEWER);
        verifyNoInteractions(throttle);                       // no admin code, so no admin-code budget is touched
    }

    @Test
    void aWrongAdminCodeIsCounted() {
        assertThatThrownBy(() -> service("SECRET-CODE").register(TestData.registerCommand("WRONG")))
                .isInstanceOf(InvalidAdminCodeException.class);

        InOrder order = inOrder(throttle);
        order.verify(throttle).beforeAdminCode(TestData.IP);
        order.verify(throttle).adminCodeFailed(TestData.IP);
    }

    @Test
    void aCorrectAdminCodeIsCheckedAgainstTheBudgetButNotCounted() {
        stubSave();

        service("SECRET-CODE").register(TestData.registerCommand("SECRET-CODE"));

        verify(throttle).beforeAdminCode(TestData.IP);
        verify(throttle, never()).adminCodeFailed(any());
    }

    @Test
    void aLockedOutAdminCodeGuessIsRejectedBeforeTheCodeIsChecked() {
        doThrow(new TooManyAttemptsException(60)).when(throttle).beforeAdminCode(TestData.IP);

        assertThatThrownBy(() -> service("SECRET-CODE").register(TestData.registerCommand("SECRET-CODE")))
                .isInstanceOf(TooManyAttemptsException.class);

        verify(throttle, never()).adminCodeFailed(any());
        verifyNoInteractions(creator);
    }

    @Test
    void aSuccessfulLoginClearsTheAccountBudget() {
        Member member = TestData.member("1", Role.VIEWER, "asha@example.com");
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(member));
        when(encoder.matches("Secret@123", "hash")).thenReturn(true);
        when(tokens.issue(member)).thenReturn(new JwtTokenService.IssuedToken("jwt", 1800));

        service("X").login(login("Asha@Example.com", "Secret@123"));

        verify(throttle).beforeLogin("asha@example.com", TestData.IP);
        verify(throttle).loginSucceeded("asha@example.com");
        verify(throttle, never()).loginFailed(any(), any());
    }

    @Test
    void aFailedLoginIsCountedByEmailAndAddressWhetherOrNotTheEmailExists() {
        Member member = TestData.member("1", Role.VIEWER, "asha@example.com");
        when(repository.findByEmail("asha@example.com")).thenReturn(Optional.of(member));
        when(repository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(encoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service("X").login(login("asha@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service("X").login(login("nobody@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(throttle).loginFailed("asha@example.com", TestData.IP);
        verify(throttle).loginFailed("nobody@example.com", TestData.IP);
    }

    @Test
    void aLockedOutLoginIsRejectedWithoutTouchingTheDatabaseOrBcrypt() {
        doThrow(new TooManyAttemptsException(30)).when(throttle).beforeLogin("asha@example.com", TestData.IP);

        assertThatThrownBy(() -> service("X").login(login("asha@example.com", "Secret@123")))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(30));

        verifyNoInteractions(repository, encoder, tokens);
    }

    @Test
    void logoutRevokesEverySession() {
        Member member = TestData.member("1", Role.VIEWER, "asha@example.com");
        int before = member.getTokenVersion();
        when(repository.findById("1")).thenReturn(Optional.of(member));

        service("X").logout("1");

        assertThat(member.getTokenVersion()).isEqualTo(before + 1);
        verify(repository).save(member);
    }

    @Test
    void logoutFailsForAMissingMember() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("X").logout("x")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void changingThePasswordRevokesEverySession() {
        Member member = TestData.member("1", Role.VIEWER, "asha@example.com");
        int before = member.getTokenVersion();
        when(repository.findById("1")).thenReturn(Optional.of(member));
        when(encoder.matches("Old@1234", "hash")).thenReturn(true);
        when(encoder.matches("Secret@123", "hash")).thenReturn(false);
        when(encoder.encode("Secret@123")).thenReturn("new-hash");

        service("X").changePassword("1", change("Old@1234", "Secret@123"));

        assertThat(member.getTokenVersion()).isEqualTo(before + 1);
        verify(throttle).beforePasswordChange("1");
        verify(throttle).passwordChangeSucceeded("1");
    }

    @Test
    void aWrongCurrentPasswordIsCounted() {
        when(repository.findById("1")).thenReturn(Optional.of(TestData.member("1", Role.VIEWER, "asha@example.com")));
        when(encoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service("X").changePassword("1", change("bad", "Secret@123")))
                .isInstanceOf(InvalidPasswordException.class);

        verify(throttle).passwordChangeFailed("1");
        verify(throttle, never()).passwordChangeSucceeded(any());
    }

    @Test
    void aLockedOutPasswordChangeStopsBeforeLoadingTheMember() {
        doThrow(new TooManyAttemptsException(10)).when(throttle).beforePasswordChange("1");

        assertThatThrownBy(() -> service("X").changePassword("1", change("a", "Secret@123")))
                .isInstanceOf(TooManyAttemptsException.class);

        verifyNoInteractions(repository, encoder);
    }
}
