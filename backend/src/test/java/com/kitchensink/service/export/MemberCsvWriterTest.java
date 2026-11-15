package com.kitchensink.service.export;




import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberCsvWriterTest {

    @Test
    void quotesAndEscapesCells() {
        assertThat(MemberCsvWriter.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(MemberCsvWriter.cell("x,y")).isEqualTo("\"x,y\"");
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(MemberCsvWriter.cell("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(MemberCsvWriter.cell("+cmd")).isEqualTo("\"'+cmd\"");
        assertThat(MemberCsvWriter.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(MemberCsvWriter.cell("-5")).isEqualTo("\"'-5\"");
        assertThat(MemberCsvWriter.cell("Normal")).isEqualTo("\"Normal\"");
    }
}
