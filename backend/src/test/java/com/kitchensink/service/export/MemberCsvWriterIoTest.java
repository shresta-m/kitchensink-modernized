package com.kitchensink.service.export;




import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.dto.imports.ImportIssue;
import com.kitchensink.dto.imports.ImportIssueCode;
import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.dto.imports.RowValidation;
import com.kitchensink.model.Member;
import com.kitchensink.observability.MicrometerMemberMetrics;
import com.kitchensink.service.export.MemberCsvWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberCsvWriterIoTest {


    // ---- MemberCsvWriter ----------------------------------------------------------------------

    private static final class BrokenWriter extends Writer {
        @Override public void write(char[] c, int o, int l) throws IOException { throw new IOException("disk full"); }
        @Override public void flush() { }
        @Override public void close() { }
    }

    @Test
    void csvWriterWritesHeaderAndRows() {
        MemberCsvWriter csv = new MemberCsvWriter();
        StringWriter out = new StringWriter();
        csv.writeHeader(out);
        csv.writeRow(out, Member.register("Ann", "ann@example.com", "2125551212", Instant.EPOCH));

        assertThat(out.toString()).isEqualTo(
                "name,email,phoneNumber,createdAt\r\n\"Ann\",\"ann@example.com\",\"2125551212\",\"1970-01-01T00:00:00Z\"\r\n");
    }

    @Test
    void csvWriterTurnsIoFailuresIntoUncheckedOnes() {
        MemberCsvWriter csv = new MemberCsvWriter();
        assertThatThrownBy(() -> csv.writeHeader(new BrokenWriter())).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void csvCellHandlesNullAndEmpty() {
        assertThat(MemberCsvWriter.cell(null)).isEqualTo("\"\"");
        assertThat(MemberCsvWriter.cell("")).isEqualTo("\"\"");
    }

}
