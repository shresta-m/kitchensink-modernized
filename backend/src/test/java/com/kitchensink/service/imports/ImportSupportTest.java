package com.kitchensink.service.imports;




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

class ImportSupportTest {


    // ---- ImportRowValidator -------------------------------------------------------------------

    private final ImportRowValidator validator =
            new ImportRowValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validRowsHaveNoIssues() {
        RowValidation result = validator.validate(new RawMemberRow(2, "Ann", "ann@example.com", "2125551212"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.details().email()).isEqualTo("ann@example.com");
    }

    @Test
    void invalidRowsGetOneIssuePerFieldWithTheSheetRowNumber() {
        RowValidation result = validator.validate(new RawMemberRow(7, "R2D2", "nope", "12"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.issues()).extracting(ImportIssue::field).containsExactly("email", "name", "phoneNumber");
        assertThat(result.issues()).allSatisfy(i -> {
            assertThat(i.row()).isEqualTo(7);
            assertThat(i.code()).isEqualTo(ImportIssueCode.VALIDATION_FAILED);
            assertThat(i.message()).isNotBlank();
        });
    }

    @Test
    void multipleViolationsOnOneFieldAreJoinedInStableOrder() {
        RowValidation result = validator.validate(new RawMemberRow(2, "", "a@b.co", "2125551212"));

        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).field()).isEqualTo("name");
    }


    // ---- ExcelTemplateWriter ------------------------------------------------------------------

    @Test
    void templateIsAZipContainer() {
        byte[] bytes = new ExcelTemplateWriter().template();
        assertThat(bytes).hasSizeGreaterThan(100);
        assertThat((char) bytes[0]).isEqualTo('P');
        assertThat((char) bytes[1]).isEqualTo('K');
    }
}
