package com.kitchensink.security.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryAttemptLimiterTest {

    /** A clock the test can move. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-17T08:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(10);

    private MovableClock clock;
    private InMemoryAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        limiter = new InMemoryAttemptLimiter(clock, 3, WINDOW, LOCKOUT, 100);
    }

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(key);
        }
    }

    private long lockedFor(String key) {
        try {
            limiter.checkAllowed(key);
            return 0;
        } catch (TooManyAttemptsException e) {
            return e.getRetryAfterSeconds();
        }
    }

    @Test
    void anUnknownKeyIsAllowed() {
        assertThatCode(() -> limiter.checkAllowed("a")).doesNotThrowAnyException();
    }

    @Test
    void failuresBelowTheLimitDoNotLock() {
        fail("a", 2);

        assertThat(lockedFor("a")).isZero();
    }

    @Test
    void reachingTheLimitLocksTheKeyAndReportsWhenToRetry() {
        fail("a", 3);

        assertThat(lockedFor("a")).isEqualTo(601);                    // 10 minutes, rounded up
        assertThatThrownBy(() -> limiter.checkAllowed("a")).isInstanceOf(TooManyAttemptsException.class);
        assertThat(lockedFor("b")).isZero();                          // other keys are unaffected
    }

    @Test
    void theLockoutEndsOnItsOwn() {
        fail("a", 3);

        clock.advance(LOCKOUT);

        assertThat(lockedFor("a")).isZero();
    }

    @Test
    void failuresOutsideTheWindowAreForgotten() {
        fail("a", 2);
        clock.advance(WINDOW.plusSeconds(1));

        fail("a", 2);                                                  // 2 old + 2 new would be 4, but the old ones expired

        assertThat(lockedFor("a")).isZero();
    }

    @Test
    void everyLockoutHasTheSameLength() {
        for (int round = 0; round < 3; round++) {
            fail("a", 3);
            assertThat(lockedFor("a")).isEqualTo(601);                // 10 minutes, rounded up, every time
            clock.advance(LOCKOUT);
        }
    }

    @Test
    void aSuccessClearsTheFailures() {
        fail("a", 3);
        limiter.recordSuccess("a");

        assertThat(lockedFor("a")).isZero();
        fail("a", 2);
        assertThat(lockedFor("a")).isZero();                          // the old failures are gone, 2 is below the limit
    }

    @Test
    void failuresWhileLockedDoNotExtendTheLockoutBeyondTheNextStrike() {
        fail("a", 3);
        limiter.recordFailure("a");                                    // a client ignoring the 429

        assertThat(lockedFor("a")).isEqualTo(601);
    }

    // ---------- bounded memory ----------

    @Test
    void whenFullExpiredEntriesAreDroppedToMakeRoom() {
        InMemoryAttemptLimiter small = new InMemoryAttemptLimiter(clock, 3, WINDOW, LOCKOUT, 2);
        small.recordFailure("a");
        small.recordFailure("b");
        clock.advance(WINDOW.plusSeconds(1));                          // both windows are over

        small.recordFailure("c");                                      // evicts a and b, then tracks c
        small.recordFailure("c");
        small.recordFailure("c");

        assertThatThrownBy(() -> small.checkAllowed("c")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void whenFullOfLiveEntriesNewKeysAreStillTracked() {
        InMemoryAttemptLimiter small = new InMemoryAttemptLimiter(clock, 3, WINDOW, LOCKOUT, 2);
        small.recordFailure("a");
        small.recordFailure("b");

        fail3(small, "new");                                           // nothing is evictable, the new key is counted anyway

        assertThatThrownBy(() -> small.checkAllowed("new")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void lockedEntriesSurviveTheSweepUntilTheirLockoutEnds() {
        InMemoryAttemptLimiter small = new InMemoryAttemptLimiter(clock, 1, WINDOW, LOCKOUT, 2);
        small.recordFailure("a");                                      // locked at once (limit 1)
        small.recordFailure("b");

        small.recordFailure("c");                                      // full: the sweep finds nothing idle
        assertThatThrownBy(() -> small.checkAllowed("a")).isInstanceOf(TooManyAttemptsException.class);

        clock.advance(WINDOW.plusMinutes(1));                          // lockouts and windows are over
        small.recordFailure("d");                                      // the sweep now drops a, b and c
        assertThatCode(() -> small.checkAllowed("a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> small.checkAllowed("d")).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void anEntryWithAnOpenWindowIsNotEvicted() {
        InMemoryAttemptLimiter small = new InMemoryAttemptLimiter(clock, 5, WINDOW, LOCKOUT, 1);
        small.recordFailure("a");
        clock.advance(WINDOW.minusSeconds(1));                         // window still open, nothing locked

        small.recordFailure("b");                                      // the sweep must keep a
        for (int i = 0; i < 4; i++) {
            small.recordFailure("a");                                  // 1 + 4 = 5 failures: only true if a was kept
        }

        assertThatThrownBy(() -> small.checkAllowed("a")).isInstanceOf(TooManyAttemptsException.class);
    }

    private static void fail3(InMemoryAttemptLimiter target, String key) {
        for (int i = 0; i < 3; i++) {
            target.recordFailure(key);
        }
    }
}
