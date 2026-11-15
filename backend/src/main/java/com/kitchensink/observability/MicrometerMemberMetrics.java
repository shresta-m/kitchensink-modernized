package com.kitchensink.observability;




import com.kitchensink.dto.imports.ImportReport;
import com.kitchensink.service.port.MemberMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MicrometerMemberMetrics implements MemberMetrics {

    private final MeterRegistry registry;

    public MicrometerMemberMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void memberCreated() {
        registry.counter("members.operations", "operation", "create").increment();
    }

    @Override
    public void memberUpdated() {
        registry.counter("members.operations", "operation", "update").increment();
    }

    @Override
    public void membersDeleted(int count) {
        registry.counter("members.operations", "operation", "delete").increment(count);
    }

    @Override
    public void batchCompleted(int size, boolean success, Duration duration) {
        String outcome = success ? "success" : "failure";
        registry.counter("members.import.batches", "outcome", outcome).increment();
        registry.timer("members.import.batch.duration", "outcome", outcome).record(duration);
    }

    @Override
    public void importCompleted(ImportReport r, Duration duration) {
        registry.counter("members.import.rows", "outcome", "inserted").increment(r.inserted());
        registry.counter("members.import.rows", "outcome", "duplicate").increment(r.duplicatesSkipped());
        registry.counter("members.import.rows", "outcome", "invalid").increment(r.invalid());
        registry.counter("members.import.rows", "outcome", "failed").increment(r.failed());
        registry.timer("members.import.duration").record(duration);
    }
}
