package com.kitchensink.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.member.model.MemberSearchCriteria;
import com.kitchensink.member.model.MemberStatus;
import com.kitchensink.member.model.Role;
import org.junit.jupiter.api.Test;

class MemberQueryBuilderTest {

    private final MemberQueryBuilder builder = new MemberQueryBuilder();

    private String query(String search, Role role, MemberStatus status) {
        return builder.build(new MemberSearchCriteria(search, role, status)).getQueryObject().toString();
    }

    @Test
    void noFiltersMeansMatchEverything() {
        assertThat(builder.build(new MemberSearchCriteria(null, null, null)).getQueryObject()).isEmpty();
        assertThat(builder.build(new MemberSearchCriteria("   ", null, null)).getQueryObject()).isEmpty();
    }

    @Test
    void textSearchIsLowercasePrefixOnNameAndEmail() {
        String q = query("  Asha ", null, null);

        assertThat(q).contains("nameLower", "email", "^asha").doesNotContain("phoneNumber");
    }

    @Test
    void numericSearchAlsoMatchesPhoneDigits() {
        String q = query("98-25", null, null);

        assertThat(q).contains("phoneNumber", "^9825");
    }

    @Test
    void searchWithNoDigitsSkipsPhone() {
        assertThat(query("+", null, null)).doesNotContain("phoneNumber");
    }

    @Test
    void regexCharactersAreEscaped() {
        assertThat(query("a.b*", null, null)).contains("^a\\.b\\*");
    }

    @Test
    void roleAndStatusFilters() {
        assertThat(query(null, Role.ADMIN, null)).contains("role").doesNotContain("mustChangePassword");
        assertThat(query(null, null, MemberStatus.TEMP_PASSWORD)).contains("mustChangePassword=true");
        assertThat(query(null, null, MemberStatus.ACTIVE)).contains("mustChangePassword=false");
    }

    @Test
    void pendingMembersAreMatchedByTheFlag() {
        assertThat(query(null, null, MemberStatus.PENDING_APPROVAL)).contains("pendingApproval=true");
    }

    @Test
    void approvedStatusesAlsoMatchMembersStoredBeforeApprovalExisted() {
        // those documents have no pendingApproval field, and {pendingApproval: false} would not match a missing field
        assertThat(query(null, null, MemberStatus.ACTIVE)).contains("$ne=true").doesNotContain("pendingApproval=false");
        assertThat(query(null, null, MemberStatus.TEMP_PASSWORD)).contains("$ne=true").doesNotContain("pendingApproval=false");
    }

    @Test
    void filtersAreCombinedWithAnd() {
        assertThat(query("asha", Role.VIEWER, MemberStatus.ACTIVE)).contains("$and", "role", "mustChangePassword", "$or");
    }
}
