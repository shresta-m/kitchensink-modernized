package com.kitchensink.service.impl;



import com.kitchensink.config.AppProperties;
import com.kitchensink.dto.imports.ImportBatchOutcome;
import com.kitchensink.dto.imports.ImportCandidate;
import com.kitchensink.dto.imports.ImportIssue;
import com.kitchensink.dto.imports.ImportIssueCode;
import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.dto.imports.RowValidation;
import com.kitchensink.model.Member;
import com.kitchensink.service.MemberImportService;
import com.kitchensink.service.imports.InFileDuplicateTracker;
import com.kitchensink.service.port.ImportBatchWriter;
import com.kitchensink.service.port.ImportMetrics;
import com.kitchensink.service.port.ImportTemplateProvider;
import com.kitchensink.service.port.MemberFileReader;
import com.kitchensink.service.port.RowValidator;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a bulk import: read, validate, drop in-file duplicates (first occurrence wins; sequential
 * so it stays deterministic), hand the survivors to the {@link ImportBatchWriter}, then assemble the report.
 * Every collaborator is a port, so format, validation, persistence and metrics can change independently.
 */
@Service
public class MemberImportServiceImpl implements MemberImportService {

    private static final Logger log = LoggerFactory.getLogger(MemberImportServiceImpl.class);

    private final MemberFileReader reader;
    private final ImportTemplateProvider templates;
    private final RowValidator validator;
    private final ImportBatchWriter writer;
    private final ImportMetrics metrics;
    private final Clock clock;
    private final AppProperties.BulkImport settings;

    public MemberImportServiceImpl(
            MemberFileReader reader,
            ImportTemplateProvider templates,
            RowValidator validator,
            ImportBatchWriter writer,
            ImportMetrics metrics,
            Clock clock,
            AppProperties props) {
        this.reader = reader;
        this.templates = templates;
        this.validator = validator;
        this.writer = writer;
        this.metrics = metrics;
        this.clock = clock;
        this.settings = props.bulkImport();
    }

    @Override
    public ImportReport importMembers(InputStream file) {
        Instant started = clock.instant();
        List<RawMemberRow> rows = reader.read(file, settings.maxRows());
        Screening screening = screen(rows, started);

        List<ImportBatchOutcome> outcomes = writer.write(screening.candidates);
        Tally tally = tally(outcomes, screening);

        ImportReport report = buildReport(rows.size(), screening, tally);
        Duration took = Duration.between(started, clock.instant());
        metrics.importCompleted(report, took);
        log.info("Import finished rows={} inserted={} duplicates={} invalid={} failed={} batches={} tookMs={}",
                report.totalRows(), report.inserted(), report.duplicatesSkipped(), report.invalid(), report.failed(),
                outcomes.size(), took.toMillis());
        return report;
    }

    /** Mutable working state of the first pass (validation and in-file duplicate detection). */
    private static final class Screening {
        final List<ImportIssue> issues = new ArrayList<>();
        final List<ImportCandidate> candidates = new ArrayList<>();
        int invalid;
        int duplicates;
    }

    private record Tally(int inserted, int duplicates, int failed) {}

    /** Validates every row and drops repeats of an email already seen in this file (first occurrence wins). */
    private Screening screen(List<RawMemberRow> rows, Instant now) {
        Screening result = new Screening();
        InFileDuplicateTracker seen = new InFileDuplicateTracker();
        for (RawMemberRow row : rows) {
            RowValidation validation = validator.validate(row);
            if (!validation.isValid()) {
                result.invalid++;
                result.issues.addAll(validation.issues());
                continue;
            }
            Member member = Member.register(validation.details().name(), validation.details().email(),
                    validation.details().phoneNumber(), now);
            OptionalInt firstRow = seen.firstSeenAt(member.getEmail(), row.rowNumber());
            if (firstRow.isPresent()) {
                result.duplicates++;
                result.issues.add(new ImportIssue(row.rowNumber(), "email", ImportIssueCode.DUPLICATE_IN_FILE,
                        "Duplicate of row " + firstRow.getAsInt() + " in this file"));
            } else {
                result.candidates.add(new ImportCandidate(row.rowNumber(), member));
            }
        }
        return result;
    }

    /** Folds the batch outcomes into totals and adds an issue for every row the database rejected. */
    private static Tally tally(List<ImportBatchOutcome> outcomes, Screening screening) {
        int inserted = 0;
        int duplicates = screening.duplicates;
        int failed = 0;
        for (ImportBatchOutcome outcome : outcomes) {
            inserted += outcome.inserted();
            for (int row : outcome.duplicateRows()) {
                duplicates++;
                screening.issues.add(new ImportIssue(row, "email", ImportIssueCode.DUPLICATE_EMAIL, "Already registered"));
            }
            for (int row : outcome.failedRows()) {
                failed++;
                screening.issues.add(new ImportIssue(row, "-", ImportIssueCode.PROCESSING_ERROR,
                        "Could not be saved; retry these rows"));
            }
        }
        return new Tally(inserted, duplicates, failed);
    }

    private ImportReport buildReport(int totalRows, Screening screening, Tally tally) {
        List<ImportIssue> issues = screening.issues;
        issues.sort(Comparator.comparingInt(ImportIssue::row));
        boolean truncated = issues.size() > settings.maxErrors();
        List<ImportIssue> shown = truncated ? List.copyOf(issues.subList(0, settings.maxErrors())) : List.copyOf(issues);
        return new ImportReport(totalRows, tally.inserted(), tally.duplicates(), screening.invalid, tally.failed(),
                shown, truncated);
    }

    @Override
    public byte[] template() {
        return templates.template();
    }
}
