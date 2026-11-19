package com.kitchensink.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.TestData;
import com.kitchensink.member.model.Member;
import com.kitchensink.member.model.MemberDetails;
import com.kitchensink.member.model.Role;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberCsvWriterTest {

    private static final String HEADER = "﻿Name,Email,Country code,Phone,Role,Status,Added\r\n";

    private final MemberCsvWriter writer = new MemberCsvWriter();

    private String csv(Member... members) {
        return new String(writer.write(List.of(members)), StandardCharsets.UTF_8);
    }

    private static Member named(String name) {
        Member m = mock(Member.class);
        when(m.getName()).thenReturn(name);
        return m;
    }

    @Test
    void writesBomHeaderAndRows() {
        Member viewer = TestData.member("1", Role.VIEWER, "asha@example.com");
        Member admin = Member.createByAdmin(TestData.details("ravi@example.com"), Role.ADMIN, "h", TestData.NOW);

        assertThat(csv(viewer, admin)).isEqualTo(HEADER
                + "Asha Rao,asha@example.com,+91,8123456789,VIEWER,ACTIVE,2026-11-17T08:00:00Z\r\n"
                + "Asha Rao,ravi@example.com,+91,8123456789,ADMIN,TEMP_PASSWORD,2026-11-17T08:00:00Z\r\n");
    }

    @Test
    void emptyListWritesOnlyTheHeader() {
        assertThat(csv()).isEqualTo(HEADER);
    }

    @Test
    void missingValuesBecomeEmptyCells() {
        assertThat(csv(mock(Member.class))).isEqualTo(HEADER + ",,,,,,\r\n");
        assertThat(csv(named(""))).isEqualTo(HEADER + ",,,,,,\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=SUM(1)", "+1", "-1", "@cmd"})
    void neutralizesFormulas(String name) {
        Member m = Member.register(new MemberDetails(name, "a@example.com", "+91", "8123456789"),
                Role.VIEWER, "h", false, TestData.NOW);
        assertThat(csv(m)).startsWith(HEADER + "'" + name + ",");
    }

    @Test
    void neutralizesTabAndCarriageReturnPrefixes() {
        assertThat(csv(named("\tx"))).contains("'\tx,");
        assertThat(csv(named("\rx"))).contains("\"'\rx\",");
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        assertThat(csv(named("Rao, Asha"))).contains("\"Rao, Asha\",");
        assertThat(csv(named("Say \"hi\""))).contains("\"Say \"\"hi\"\"\",");
        assertThat(csv(named("a\nb"))).contains("\"a\nb\",");
        assertThat(csv(named("a\rb"))).contains("\"a\rb\",");
        assertThat(csv(named("plain"))).contains("\r\nplain,");
    }
}
