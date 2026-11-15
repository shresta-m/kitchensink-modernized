package com.kitchensink.service.imports;



import com.kitchensink.config.AppProperties;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.imports.ImportBatchOutcome;
import com.kitchensink.dto.imports.ImportCandidate;
import com.kitchensink.service.port.ImportBatchWriter;
import com.kitchensink.service.port.ImportMetrics;
import com.kitchensink.service.port.MemberStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Splits candidates into fixed-size batches and writes them concurrently on a bounded pool. The unique
 * email index is the final arbiter: collisions come back as duplicate rows. A batch that fails for
 * infrastructure reasons is isolated, so its rows are reported and the other batches are unaffected.
 */
@Service
public class ParallelImportBatchWriter implements ImportBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(ParallelImportBatchWriter.class);

    private final MemberStore members;
    private final ImportMetrics metrics;
    private final Executor executor;
    private final int batchSize;

    public ParallelImportBatchWriter(
            MemberStore members,
            ImportMetrics metrics,
            @Qualifier("importExecutor") Executor executor,
            AppProperties props) {
        this.members = members;
        this.metrics = metrics;
        this.executor = executor;
        this.batchSize = props.bulkImport().batchSize();
    }

    @Override
    public List<ImportBatchOutcome> write(List<ImportCandidate> candidates) {
        List<CompletableFuture<ImportBatchOutcome>> futures = partition(candidates, batchSize).stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> writeBatch(batch), executor))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private ImportBatchOutcome writeBatch(List<ImportCandidate> batch) {
        long start = System.nanoTime();
        try {
            BatchInsertResult result = members.insertBatch(batch.stream().map(ImportCandidate::member).toList());
            List<Integer> duplicateRows = result.duplicateIndexes().stream()
                    .map(i -> batch.get(i).rowNumber())
                    .toList();
            metrics.batchCompleted(batch.size(), true, Duration.ofNanos(System.nanoTime() - start));
            return new ImportBatchOutcome(result.inserted(), duplicateRows, List.of());
        } catch (RuntimeException e) {
            log.error("Import batch failed size={}", batch.size(), e);
            metrics.batchCompleted(batch.size(), false, Duration.ofNanos(System.nanoTime() - start));
            return new ImportBatchOutcome(0, List.of(), batch.stream().map(ImportCandidate::rowNumber).toList());
        }
    }

    public static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return batches;
    }
}
