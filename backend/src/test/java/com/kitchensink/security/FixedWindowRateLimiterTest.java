package com.kitchensink.security;




import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final AtomicLong epochSecond = new AtomicLong(600); // start of a 60s window
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochSecond(epochSecond.get()); }
    };

    @Test
    void allowsUpToTheLimitThenBlocksUntilTheNextWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 60, clock);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.retryAfterSeconds()).isEqualTo(60);

        epochSecond.addAndGet(60);
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void keysAreIndependent() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, 60, clock);

        assertThat(limiter.tryAcquire("1.1.1.1|/login")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1|/login")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2|/login")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1|/register")).isTrue();
    }
}
