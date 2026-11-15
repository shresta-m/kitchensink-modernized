package com.kitchensink.service;





import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kitchensink.config.AppProperties;
import com.kitchensink.dto.imports.BatchInsertResult;
import com.kitchensink.dto.imports.ImportIssue;
import com.kitchensink.dto.imports.ImportIssueCode;
import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.dto.imports.RawMemberRow;
import com.kitchensink.model.Member;
import com.kitchensink.observability.MicrometerMemberMetrics;
import com.kitchensink.service.impl.MemberImportServiceImpl;
import com.kitchensink.service.imports.ExcelTemplateWriter;
import com.kitchensink.service.imports.ImportRowValidator;
import com.kitchensink.service.imports.ParallelImportBatchWriter;
import com.kitchensink.service.port.ImportBatchWriter;
import com.kitchensink.service.port.MemberFileReader;
import com.kitchensink.service.port.MemberMetrics;
import com.kitchensink.service.port.MemberStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class MemberImportServiceTest {

    /** Honors the same contract as the Mongo implementation: unique email, duplicates reported by index. */
    private final Set<String> emails = ConcurrentHashMap.newKeySet();
    private final Set<String> failingEmails = ConcurrentHashMap.newKeySet();
    private final AtomicInteger batchCalls = new AtomicInteger();
    private final MemberStore repo = mock(MemberStore.class);
    private final List<RawMemberRow> sheet = new ArrayList<>();
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        when(repo.insertBatch(any())).thenAnswer(inv -> {
            List<Member> members = inv.getArgument(0);
            batchCalls.incrementAndGet();
            if (members.stream().anyMatch(m -> failingEmails.contains(m.getEmail()))) {
                throw new IllegalStateException("simulated database outage");
            }
            int inserted = 0;
            List<Integer> duplicates = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                if (emails.add(members.get(i).getEmail())) {
                    inserted++;
                } else {
                    duplicates.add(i);
                }
            }
            return new BatchInsertResult(inserted, duplicates);
        });
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.initialize();
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    private MemberImportServiceImpl service(int batchSize, int maxErrors) {
        AppProperties props = new AppProperties(
                new AppProperties.Security("x".repeat(32), "test", Duration.ofMinutes(5), false, List.of("http://localhost"), 10, ""),
                new AppProperties.BulkImport(batchSize, 4, 1000, maxErrors),
                new AppProperties.Bootstrap(null, null, null, null));
        MemberFileReader reader = mock(MemberFileReader.class);
        when(reader.read(any(), anyInt())).thenReturn(sheet);
        ImportRowValidator validator = new ImportRowValidator(Validation.buildDefaultValidatorFactory().getValidator());
        MemberMetrics metrics = new MicrometerMemberMetrics(new SimpleMeterRegistry());
        ImportBatchWriter writer = new ParallelImportBatchWriter(repo, metrics, (Executor) executor, props);
        return new MemberImportServiceImpl(
                reader, new ExcelTemplateWriter(), validator, writer, metrics, Clock.systemUTC(), props);
    }

    private static RawMemberRow row(int n, String name, String email, String phone) {
        return new RawMemberRow(n, name, email, phone);
    }

    @Test
    void insertsValidRowsAcrossManyBatchesInParallel() {
        IntStream.rangeClosed(2, 101).forEach(i -> sheet.add(row(i, "Person", "p" + i + "@example.com", "2125551212")));

        ImportReport report = service(10, 100).importMembers(null);

        assertThat(report.totalRows()).isEqualTo(100);
        assertThat(report.inserted()).isEqualTo(100);
        assertThat(report.errors()).isEmpty();
        assertThat(batchCalls.get()).isEqualTo(10);
        assertThat(emails).hasSize(100);
    }

    @Test
    void reportsInvalidRowsWithRowNumberAndField() {
        sheet.add(row(2, "Good", "good@example.com", "2125551212"));
        sheet.add(row(3, "Bad1", "not-an-email", "2125551212"));
        sheet.add(row(4, "Bad", "ok@example.com", "123"));

        ImportReport report = service(10, 100).importMembers(null);

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.invalid()).isEqualTo(2);
        assertThat(report.errors()).extracting(ImportIssue::row, ImportIssue::field)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3, "email"),
                        org.assertj.core.groups.Tuple.tuple(3, "name"),
                        org.assertj.core.groups.Tuple.tuple(4, "phoneNumber"));
    }

    @Test
    void firstOccurrenceWinsForInFileDuplicatesAndEmailsAreCaseInsensitive() {
        sheet.add(row(2, "First", "dup@example.com", "2125551212"));
        sheet.add(row(3, "Second", "DUP@Example.com", "2125551213"));

        ImportReport report = service(10, 100).importMembers(null);

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.duplicatesSkipped()).isEqualTo(1);
        assertThat(report.errors()).singleElement().satisfies(i -> {
            assertThat(i.row()).isEqualTo(3);
            assertThat(i.code()).isEqualTo(ImportIssueCode.DUPLICATE_IN_FILE);
            assertThat(i.message()).contains("row 2");
        });
    }

    @Test
    void emailsAlreadyInDatabaseAreReportedAsDuplicates() {
        emails.add("existing@example.com");
        sheet.add(row(2, "New", "new@example.com", "2125551212"));
        sheet.add(row(3, "Old", "existing@example.com", "2125551213"));

        ImportReport report = service(10, 100).importMembers(null);

        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.duplicatesSkipped()).isEqualTo(1);
        assertThat(report.errors()).singleElement().satisfies(i -> {
            assertThat(i.row()).isEqualTo(3);
            assertThat(i.code()).isEqualTo(ImportIssueCode.DUPLICATE_EMAIL);
        });
    }

    @Test
    void aFailingBatchIsIsolatedAndOtherBatchesStillSucceed() {
        // batch size 2: rows 2-3 | 4-5 | 6. The batch containing "boom" fails as a whole.
        sheet.add(row(2, "A", "a@example.com", "2125551212"));
        sheet.add(row(3, "B", "b@example.com", "2125551212"));
        sheet.add(row(4, "C", "boom@example.com", "2125551212"));
        sheet.add(row(5, "D", "d@example.com", "2125551212"));
        sheet.add(row(6, "E", "e@example.com", "2125551212"));
        failingEmails.add("boom@example.com");

        ImportReport report = service(2, 100).importMembers(null);

        assertThat(report.inserted()).isEqualTo(3);
        assertThat(report.failed()).isEqualTo(2);
        assertThat(report.errors()).extracting(ImportIssue::row).containsExactly(4, 5);
        assertThat(report.errors()).allSatisfy(i -> assertThat(i.code()).isEqualTo(ImportIssueCode.PROCESSING_ERROR));
    }

    @Test
    void errorListIsCappedAndFlagged() {
        IntStream.rangeClosed(2, 21).forEach(i -> sheet.add(row(i, "Bad", "nope", "1")));

        ImportReport report = service(10, 5).importMembers(null);

        assertThat(report.errors()).hasSize(5);
        assertThat(report.errorsTruncated()).isTrue();
        assertThat(report.invalid()).isEqualTo(20);
    }

    @Test
    void partitionSplitsIntoFixedSizeChunks() {
        assertThat(ParallelImportBatchWriter.partition(List.of(1, 2, 3, 4, 5), 2)).containsExactly(
                List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(ParallelImportBatchWriter.partition(List.<Integer>of(), 3)).isEmpty();
    }
}
