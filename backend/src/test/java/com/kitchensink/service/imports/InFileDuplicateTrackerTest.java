package com.kitchensink.service.imports;




import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class InFileDuplicateTrackerTest {

    @Test
    void firstOccurrenceIsRecordedAndLaterOnesPointBackToIt() {
        InFileDuplicateTracker tracker = new InFileDuplicateTracker();

        assertThat(tracker.firstSeenAt("a@x.com", 2)).isEmpty();
        assertThat(tracker.firstSeenAt("b@x.com", 3)).isEmpty();
        assertThat(tracker.firstSeenAt("a@x.com", 7)).isEqualTo(OptionalInt.of(2));
        assertThat(tracker.firstSeenAt("a@x.com", 9)).isEqualTo(OptionalInt.of(2));
    }
}
