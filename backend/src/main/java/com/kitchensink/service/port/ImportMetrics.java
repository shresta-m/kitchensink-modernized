package com.kitchensink.service.port;



import com.kitchensink.dto.imports.ImportReport;
import java.time.Duration;

/** Port: timings and outcomes of bulk imports. */
public interface ImportMetrics {

    void batchCompleted(int size, boolean success, Duration duration);

    void importCompleted(ImportReport report, Duration duration);
}
