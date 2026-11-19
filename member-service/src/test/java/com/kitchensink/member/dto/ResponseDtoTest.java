package com.kitchensink.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.TestData;
import com.kitchensink.auth.dto.LoginResponse;
import com.kitchensink.auth.dto.RegisterResponse;
import com.kitchensink.common.dto.FieldViolation;
import com.kitchensink.common.dto.PageResponse;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ResponseDtoTest {

    @Test
    void memberResponseCarriesNoPasswordHash() {
        Member m = Member.createByAdmin(TestData.details("a@example.com"), Role.VIEWER, "secret-hash", TestData.NOW);
        MemberResponse response = MemberResponse.from(m);

        assertThat(response.getName()).isEqualTo("Asha Rao");
        assertThat(response.getCountryCode()).isEqualTo("+91");
        assertThat(response.getStatus()).isEqualTo(MemberStatus.TEMP_PASSWORD);
        assertThat(response.getCreatedAt()).isEqualTo(TestData.NOW);
        assertThat(response.getVersion()).isNull();                      // not persisted yet
        assertThat(response.toString()).doesNotContain("secret-hash");
    }

    @Test
    void registerResponseHasOnlyPublicFields() {
        RegisterResponse response = RegisterResponse.from(TestData.member("1", Role.ADMIN, "a@example.com"));

        assertThat(response.getId()).isEqualTo("1");
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void loginResponseCarriesTokenAndMember() {
        LoginResponse response = LoginResponse.of(TestData.member("1", Role.VIEWER, "a@example.com"), "jwt", 1800);

        assertThat(response.getAccessToken()).isEqualTo("jwt");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1800);
        assertThat(response.getMember().getEmail()).isEqualTo("a@example.com");
    }

    @Test
    void pageResponseMapsContentAndPagingFields() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(2, 2), 6);

        PageResponse<String> response = PageResponse.of(page, n -> "n" + n);

        assertThat(response.getContent()).containsExactly("n1", "n2");
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(6);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    void passwordsNeverAppearInToString() {
        MemberResponse member = MemberResponse.from(TestData.member("1", Role.VIEWER, "a@example.com"));

        assertThat(CreateMemberResponse.builder().member(member).temporaryPassword("Temp1234").build().toString())
                .contains("***").doesNotContain("Temp1234");
        assertThat(ResetPasswordResponse.builder().temporaryPassword("Temp1234").build().toString())
                .contains("***").doesNotContain("Temp1234");
    }

    @Test
    void loginResponseHidesTheTokenInToString() {
        LoginResponse response = LoginResponse.builder().accessToken("tok.en.value").tokenType("Bearer")
                .expiresIn(1800).mustChangePassword(true).build();

        assertThat(response.getExpiresIn()).isEqualTo(1800);
        assertThat(response.isMustChangePassword()).isTrue();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getAccessToken()).isEqualTo("tok.en.value");
        assertThat(response.getMember()).isNull();
        assertThat(response.toString()).doesNotContain("tok.en.value");
    }

    @Test
    void bulkDeleteResponseAndFieldViolation() {
        assertThat(BulkDeleteResponse.builder().deletedCount(3).build().getDeletedCount()).isEqualTo(3);
        FieldViolation violation = new FieldViolation("email", "bad");
        assertThat(violation.getField()).isEqualTo("email");
        assertThat(violation.getMessage()).isEqualTo("bad");
    }
}
