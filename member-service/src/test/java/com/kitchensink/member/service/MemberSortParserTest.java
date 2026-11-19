package com.kitchensink.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.common.exception.ErrorCode;
import com.kitchensink.member.exception.InvalidSortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

class MemberSortParserTest {

    private final MemberSortParser parser = new MemberSortParser();

    @Test
    void parsesFieldAndDirectionWithIdTieBreaker() {
        var orders = parser.parse("name,desc").toList();

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("nameLower");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("id");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void directionDefaultsToAscending() {
        assertThat(parser.parse("createdAt").toList().get(0).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void acceptsEveryWhitelistedField() {
        assertThat(parser.parse("email,asc").toList().get(0).getProperty()).isEqualTo("email");
        assertThat(parser.parse(" role , DESC ").toList().get(0).getProperty()).isEqualTo("role");
    }

    @ParameterizedTest
    @ValueSource(strings = {"passwordHash,asc", "name,up", "name,", "", "name,asc,extra", "NAME"})
    void rejectsAnythingOutsideTheWhitelist(String raw) {
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOfSatisfying(InvalidSortException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_SORT);
                    assertThat(e.getField()).isEqualTo("sort");
                    assertThat(e.getMessage()).contains("name, email, role, createdAt");
                });
    }

    @Test
    void sortFieldLookup() {
        assertThat(MemberSortField.fromApiName("name")).contains(MemberSortField.NAME);
        assertThat(MemberSortField.fromApiName("nope")).isEmpty();
        assertThat(MemberSortField.EMAIL.property()).isEqualTo("email");
    }
}
