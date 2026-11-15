package com.kitchensink.dto;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.dto.auth.AuthToken;
import com.kitchensink.dto.auth.LoginResponse;
import com.kitchensink.dto.common.PageResponse;
import com.kitchensink.dto.common.PageResult;
import com.kitchensink.dto.imports.ImportIssue;
import com.kitchensink.dto.imports.ImportIssueCode;
import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.dto.imports.ImportReportResponse;
import com.kitchensink.dto.imports.RowValidation;
import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberResponse;
import com.kitchensink.dto.member.MemberSearchCriteria;
import com.kitchensink.dto.member.MemberSort;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortDirection;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.dto.user.UserResponse;
import com.kitchensink.exception.domain.InvalidQueryException;
import com.kitchensink.model.Member;
import com.kitchensink.model.Role;
import com.kitchensink.model.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DtoTest {

    @Test
    void pageResultComputesTotalPages() {
        assertThat(new PageResult<>(List.of(), 0, 10, 25).totalPages()).isEqualTo(3);
        assertThat(new PageResult<>(List.of(), 0, 10, 0).totalPages()).isZero();
        assertThat(new PageResult<>(List.of(), 0, 0, 5).totalPages()).isZero();
    }

    @Test
    void pageResponseMapsContentAndKeepsPagingFields() {
        PageResult<Integer> page = new PageResult<>(List.of(1, 2), 1, 2, 5);

        PageResponse<String> response = PageResponse.from(page, i -> "n" + i, "name,asc");

        assertThat(response.content()).containsExactly("n1", "n2");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.sort()).isEqualTo("name,asc");
    }

    @Test
    void searchCriteriaTrimsAndLowercases() {
        MemberSearchCriteria c = MemberSearchCriteria.of("  AbC ");
        assertThat(c.text()).isEqualTo("AbC");
        assertThat(c.normalizedText()).isEqualTo("abc");
        assertThat(c.isEmpty()).isFalse();
        assertThat(MemberSearchCriteria.of(null).isEmpty()).isTrue();
        assertThat(MemberSearchCriteria.of("   ").isEmpty()).isTrue();
    }

    @Test
    void searchCriteriaRejectsOverlongInput() {
        String tooLong = "x".repeat(MemberSearchCriteria.MAX_LENGTH + 1);
        assertThatThrownBy(() -> MemberSearchCriteria.of(tooLong)).isInstanceOf(InvalidQueryException.class);
        assertThat(MemberSearchCriteria.of("x".repeat(MemberSearchCriteria.MAX_LENGTH)).isEmpty()).isFalse();
    }

    @Test
    void sortFieldWhitelist() {
        assertThat(MemberSort.fromParam("name")).isEqualTo(MemberSort.NAME);
        assertThat(MemberSort.fromParam("email")).isEqualTo(MemberSort.EMAIL);
        assertThat(MemberSort.fromParam("createdAt")).isEqualTo(MemberSort.CREATED_AT);
        assertThat(MemberSort.CREATED_AT.param()).isEqualTo("createdAt");
        assertThatThrownBy(() -> MemberSort.fromParam("password")).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> MemberSort.fromParam(null)).isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void sortDirectionIsCaseInsensitive() {
        assertThat(SortDirection.fromParam("ASC")).isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.fromParam("Desc")).isEqualTo(SortDirection.DESC);
        assertThatThrownBy(() -> SortDirection.fromParam("up")).isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void sortSpecParsing() {
        assertThat(SortSpec.parse("  ")).isEqualTo(SortSpec.DEFAULT);
        assertThat(SortSpec.parse("email")).isEqualTo(new SortSpec(MemberSort.EMAIL, SortDirection.ASC));
        assertThat(SortSpec.parse(" createdAt , DESC ").toParam()).isEqualTo("createdAt,desc");
        assertThatThrownBy(() -> SortSpec.parse("name,asc,extra")).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> new SortSpec(null, SortDirection.ASC)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SortSpec(MemberSort.NAME, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PageQuery(0, 10, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void memberResponseDefaultsMissingVersionToZero() {
        Member m = Member.register("Ann", "ann@example.com", "2125551212", Instant.EPOCH);
        assertThat(MemberResponse.from(m).version()).isZero();

        ReflectionTestUtils.setField(m, "version", 7L);
        ReflectionTestUtils.setField(m, "id", "id1");
        MemberResponse r = MemberResponse.from(m);
        assertThat(r.version()).isEqualTo(7);
        assertThat(r.id()).isEqualTo("id1");
        assertThat(r.email()).isEqualTo("ann@example.com");
    }

    @Test
    void userResponseNeverExposesThePasswordHash() {
        User u = new User("jane", "secret-hash", Role.ADMIN, Instant.EPOCH);
        UserResponse r = UserResponse.from(u);

        assertThat(r.username()).isEqualTo("jane");
        assertThat(r.role()).isEqualTo(Role.ADMIN);
        assertThat(r.enabled()).isTrue();
        assertThat(r.toString()).doesNotContain("secret-hash");
    }

    @Test
    void loginResponseIsBuiltFromTheToken() {
        LoginResponse r = LoginResponse.from(new AuthToken("jwt", 1800, "jane", Role.VIEWER));

        assertThat(r.accessToken()).isEqualTo("jwt");
        assertThat(r.tokenType()).isEqualTo("Bearer");
        assertThat(r.expiresIn()).isEqualTo(1800);
        assertThat(r.user()).isEqualTo(new LoginResponse.UserInfo("jane", "VIEWER"));
    }

    @Test
    void importReportResponseFlattensIssues() {
        ImportReport report = new ImportReport(3, 1, 1, 1, 0,
                List.of(new ImportIssue(2, "email", ImportIssueCode.DUPLICATE_EMAIL, "Already registered")), true);

        ImportReportResponse r = ImportReportResponse.from(report);

        assertThat(r.totalRows()).isEqualTo(3);
        assertThat(r.errorsTruncated()).isTrue();
        assertThat(r.errors()).containsExactly(new ImportReportResponse.Issue(2, "email", "DUPLICATE_EMAIL", "Already registered"));
    }

    @Test
    void rowValidationIsValidOnlyWithoutIssues() {
        MemberRequest d = new MemberRequest("Ann", "a@b.co", "2125551212");
        assertThat(new RowValidation(d, List.of()).isValid()).isTrue();
        assertThat(new RowValidation(d, List.of(new ImportIssue(2, "name", ImportIssueCode.VALIDATION_FAILED, "x"))).isValid())
                .isFalse();
    }
}
