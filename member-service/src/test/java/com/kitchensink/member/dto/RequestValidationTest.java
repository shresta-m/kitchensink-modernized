package com.kitchensink.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.TestData;
import com.kitchensink.auth.dto.ChangePasswordRequest;
import com.kitchensink.auth.dto.LoginRequest;
import com.kitchensink.auth.dto.RegisterRequest;
import com.kitchensink.member.model.Role;
import com.kitchensink.member.validation.MemberRules;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the real Bean Validation annotations on the public request classes. */
class RequestValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static <T> List<String> messages(T target, String property) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(target);
        return violations.stream()
                .filter(v -> v.getPropertyPath().toString().equals(property))
                .map(ConstraintViolation::getMessage).toList();
    }

    @Test
    void validCreateRequestHasNoViolations() {
        assertThat(VALIDATOR.validate(TestData.createRequest(Role.VIEWER))).isEmpty();
    }

    @Test
    void validRegisterRequestHasNoViolations() {
        assertThat(VALIDATOR.validate(TestData.registerRequest(null))).isEmpty();
    }

    @Test
    void updateRequestSharesTheProfileRules() {
        UpdateMemberRequest request = new UpdateMemberRequest();
        request.setName("Asha4");
        request.setEmail("asha@example.com");
        request.setCountryCode("+91");
        request.setPhone(TestData.PHONE);
        assertThat(messages(request, "name")).containsExactly(MemberRules.NAME_MESSAGE);
        assertThat(request.toDetails().name()).isEqualTo("Asha4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "Asha4", "abcdefghijklmnopqrstuvwxyz"})
    void rejectsBadNamesWithOneMessage(String name) {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setName(name);
        assertThat(messages(request, "name")).containsExactly(MemberRules.NAME_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a@b", "a@b.c", "a@@b.com", "a@b..com", "a b@c.com"})
    void rejectsBadEmails(String email) {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setEmail(email);
        assertThat(messages(request, "email")).containsExactly(MemberRules.EMAIL_MESSAGE);
    }

    @Test
    void rejectsEmailLongerThan254Characters() {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setEmail("a".repeat(250) + "@b.com");
        assertThat(messages(request, "email")).containsExactly(MemberRules.EMAIL_MESSAGE);
    }

    @Test
    void rejectsCountryCodeWithoutPlus() {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setCountryCode("91");
        assertThat(messages(request, "countryCode")).containsExactly(MemberRules.COUNTRY_CODE_MESSAGE);
    }

    @Test
    void blankPhoneReportsOnlyTheRequiredMessage() {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setPhone(" ");
        assertThat(messages(request, "phone")).containsExactly("Enter the phone number");
    }

    @Test
    void dummyPhoneIsRejectedUnderThePhoneField() {
        CreateMemberRequest request = TestData.createRequest(Role.VIEWER);
        request.setPhone("9999999999");
        assertThat(messages(request, "phone")).containsExactly("Enter a valid phone number for the selected country");
    }

    @Test
    void createRequestNeedsARole() {
        CreateMemberRequest request = TestData.createRequest(null);
        assertThat(messages(request, "role")).containsExactly("Choose a role");
    }

    @Test
    void registerRequestRejectsWeakPassword() {
        var request = TestData.registerRequest(null);
        request.setPassword("short");
        request.setConfirmPassword("short");
        assertThat(messages(request, "password")).containsExactly(MemberRules.PASSWORD_MESSAGE);
    }

    @Test
    void registerRequestRejectsMismatchedPasswordsOnTheConfirmField() {
        var request = TestData.registerRequest(null);
        request.setConfirmPassword("Other1234");
        assertThat(messages(request, "confirmPassword")).containsExactly("Passwords do not match");
    }

    @Test
    void registerRequestNeedsConfirmation() {
        var request = TestData.registerRequest(null);
        request.setConfirmPassword(null);
        assertThat(messages(request, "confirmPassword")).containsExactly("Confirm your password");
    }

    @Test
    void registerRequestToStringHidesPasswords() {
        String text = TestData.registerRequest("CODE").toString();
        assertThat(text).contains("asha@example.com").doesNotContain("Secret@123");
    }

    @Test
    void changeRoleRequestNeedsARoleAndAVersion() {
        assertThat(messages(new ChangeMemberRoleRequest(), "role")).containsExactly("Choose a role");
        assertThat(messages(new ChangeMemberRoleRequest(), "version")).containsExactly("Version is required");
    }

    @Test
    void loginRequestOnlyNeedsBothFieldsAndHidesThePassword() {
        com.kitchensink.auth.dto.LoginRequest request = new com.kitchensink.auth.dto.LoginRequest();
        assertThat(messages(request, "email")).containsExactly("Enter your email");
        assertThat(messages(request, "password")).containsExactly("Enter your password");

        request.setEmail("a@example.com");
        request.setPassword("Secret@123");
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.toString()).contains("a@example.com").doesNotContain("Secret@123");
    }

    @Test
    void changePasswordRequestRules() {
        com.kitchensink.auth.dto.ChangePasswordRequest request = new com.kitchensink.auth.dto.ChangePasswordRequest();
        assertThat(messages(request, "currentPassword")).containsExactly("Enter your current password");
        assertThat(messages(request, "newPassword")).containsExactly(MemberRules.PASSWORD_MESSAGE);
        assertThat(messages(request, "confirmPassword")).containsExactly("Confirm your new password");

        request.setCurrentPassword("Old12345");
        request.setNewPassword("Secret@123");
        request.setConfirmPassword("Other1234");
        assertThat(messages(request, "confirmPassword")).containsExactly("Passwords do not match");

        request.setConfirmPassword("Secret@123");
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.toString()).doesNotContain("Secret@123");
    }

    @Test
    void updateRequestNeedsAVersion() {
        assertThat(messages(new UpdateMemberRequest(), "version")).containsExactly("Version is required");
    }

    @Test
    void bulkDeleteRequestRules() {
        BulkDeleteRequest request = new BulkDeleteRequest();
        assertThat(messages(request, "ids")).containsExactly("Select at least one member");

        request.setIds(List.of("a", " "));
        assertThat(VALIDATOR.validate(request)).extracting(ConstraintViolation::getMessage)
                .containsExactly("Ids must not be blank");

        request.setIds(java.util.stream.IntStream.range(0, 101).mapToObj(String::valueOf).toList());
        assertThat(messages(request, "ids")).containsExactly("You can delete at most 100 members at a time");

        request.setIds(List.of("a", "b"));
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
