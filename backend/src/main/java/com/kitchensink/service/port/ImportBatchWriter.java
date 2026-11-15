package com.kitchensink.service.port;



import com.kitchensink.dto.imports.ImportBatchOutcome;
import com.kitchensink.dto.imports.ImportCandidate;
import java.util.List;

/** Port: persists validated, de-duplicated rows. Implementations decide batching and concurrency. */
public interface ImportBatchWriter {

    /** One outcome per batch. A failed batch is reported in its outcome, never thrown. */
    List<ImportBatchOutcome> write(List<ImportCandidate> candidates);
}
