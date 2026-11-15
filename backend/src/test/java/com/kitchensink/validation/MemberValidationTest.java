package com.kitchensink.validation;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.dto.member.MemberRequest;
import com.kitchensink.dto.member.MemberSort;
import com.kitchensink.dto.member.PageQuery;
import com.kitchensink.dto.member.SortDirection;
import com.kitchensink.dto.member.SortSpec;
import com.kitchensink.exception.domain.InvalidQueryException;
import com.kitchensink.model.Member;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MemberValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> invalidFields(MemberRequest d) {
        return validator.validate(d).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(new MemberRequest("John Smith", "john@example.com", "2125551212"))).isEmpty();
    }

    @Test
    void nameRules() {
        assertThat(invalidFields(new MemberRequest("", "a@b.co", "2125551212"))).containsExactly("name");
        assertThat(invalidFields(new MemberRequest("R2D2", "a@b.co", "2125551212"))).containsExactly("name");
        assertThat(invalidFields(new MemberRequest("x".repeat(26), "a@b.co", "2125551212"))).containsExactly("name");
        assertThat(invalidFields(new MemberRequest("x".repeat(25), "a@b.co", "2125551212"))).isEmpty();
    }

    @Test
    void emailRules() {
        assertThat(invalidFields(new MemberRequest("Ann", "not-an-email", "2125551212"))).containsExactly("email");
        assertThat(invalidFields(new MemberRequest("Ann", "a@b", "2125551212"))).containsExactly("email");
        assertThat(invalidFields(new MemberRequest("Ann", null, "2125551212"))).containsExactly("email");
    }

    @Test
    void phoneRules() {
        assertThat(invalidFields(new MemberRequest("Ann", "a@b.co", "123456789"))).containsExactly("phoneNumber");
        assertThat(invalidFields(new MemberRequest("Ann", "a@b.co", "1234567890123"))).containsExactly("phoneNumber");
        assertThat(invalidFields(new MemberRequest("Ann", "a@b.co", "21255512ab"))).containsExactly("phoneNumber");
        assertThat(invalidFields(new MemberRequest("Ann", "a@b.co", "212555121212"))).isEmpty();
    }

    @Test
    void registerNormalizesFields() {
        Member m = Member.register("  Ann Lee ", "  Ann.Lee@Example.COM ", " 2125551212 ", Instant.EPOCH);
        assertThat(m.getName()).isEqualTo("Ann Lee");
        assertThat(m.getNameLower()).isEqualTo("ann lee");
        assertThat(m.getEmail()).isEqualTo("ann.lee@example.com");
        assertThat(m.getPhoneNumber()).isEqualTo("2125551212");
        assertThat(m.getId()).isNull();
    }

    @Test
    void sortSpecParsesAndRejectsUnknownFields() {
        assertThat(SortSpec.parse("email,desc")).isEqualTo(new SortSpec(MemberSort.EMAIL, SortDirection.DESC));
        assertThat(SortSpec.parse(null)).isEqualTo(SortSpec.DEFAULT);
        assertThatThrownBy(() -> SortSpec.parse("password,asc")).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> SortSpec.parse("name,sideways")).isInstanceOf(InvalidQueryException.class);
    }

    @Test
    void pageQueryEnforcesBounds() {
        assertThatThrownBy(() -> new PageQuery(-1, 10, SortSpec.DEFAULT)).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0, SortSpec.DEFAULT)).isInstanceOf(InvalidQueryException.class);
        assertThatThrownBy(() -> new PageQuery(0, 101, SortSpec.DEFAULT)).isInstanceOf(InvalidQueryException.class);
        assertThat(new PageQuery(2, 10, SortSpec.DEFAULT).offset()).isEqualTo(20);
    }
}
