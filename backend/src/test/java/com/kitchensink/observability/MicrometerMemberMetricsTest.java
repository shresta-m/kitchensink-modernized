package com.kitchensink.observability;




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

class MicrometerMemberMetricsTest {


    // ---- MicrometerMemberMetrics --------------------------------------------------------------

    @Test
    void metricsAreRecordedUnderStableNames() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMemberMetrics metrics = new MicrometerMemberMetrics(registry);

        metrics.memberCreated();
        metrics.memberUpdated();
        metrics.membersDeleted(3);
        metrics.batchCompleted(10, true, Duration.ofMillis(5));
        metrics.batchCompleted(10, false, Duration.ofMillis(5));
        metrics.importCompleted(new ImportReport(10, 6, 2, 1, 1, List.of(), false), Duration.ofSeconds(1));

        assertThat(registry.get("members.operations").tag("operation", "create").counter().count()).isEqualTo(1);
        assertThat(registry.get("members.operations").tag("operation", "update").counter().count()).isEqualTo(1);
        assertThat(registry.get("members.operations").tag("operation", "delete").counter().count()).isEqualTo(3);
        assertThat(registry.get("members.import.batches").tag("outcome", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("members.import.batch.duration").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("members.import.rows").tag("outcome", "inserted").counter().count()).isEqualTo(6);
        assertThat(registry.get("members.import.rows").tag("outcome", "duplicate").counter().count()).isEqualTo(2);
        assertThat(registry.get("members.import.rows").tag("outcome", "invalid").counter().count()).isEqualTo(1);
        assertThat(registry.get("members.import.rows").tag("outcome", "failed").counter().count()).isEqualTo(1);
        assertThat(registry.get("members.import.duration").timer().count()).isEqualTo(1);
    }

}
