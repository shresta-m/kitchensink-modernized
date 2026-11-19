package com.kitchensink.security.throttle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window failure counter: {@code maxFailures} failures inside {@code window} lock the key for {@code lockout};
 * a success clears the key. When the table reaches {@code maxEntries}, entries that are neither locked nor inside an
 * open window are dropped, so memory follows live attackers rather than history.
 */
public final class InMemoryAttemptLimiter implements AttemptLimiter {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final Duration lockout;
    private final int maxEntries;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public InMemoryAttemptLimiter(Clock clock, int maxFailures, Duration window, Duration lockout, int maxEntries) {
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.lockout = lockout;
        this.maxEntries = maxEntries;
    }

    @Override
    public void checkAllowed(String key) {
        State state = states.get(key);
        if (state == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (state) {
            if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
                long seconds = Duration.between(now, state.lockedUntil).toSeconds() + 1;   // round up, never 0
                throw new TooManyAttemptsException(seconds);
            }
        }
    }

    @Override
    public void recordFailure(String key) {
        if (states.size() >= maxEntries) {
            evictIdle();
        }
        Instant now = clock.instant();
        State state = states.computeIfAbsent(key, k -> new State());
        synchronized (state) {
            if (state.windowStart == null || now.isAfter(state.windowStart.plus(window))) {
                state.windowStart = now;
                state.failures = 0;
            }
            state.failures++;
            if (state.failures >= maxFailures) {
                state.lockedUntil = now.plus(lockout);
                state.failures = 0;
                state.windowStart = null;
            }
        }
    }

    @Override
    public void recordSuccess(String key) {
        states.remove(key);
    }

    private void evictIdle() {
        Instant now = clock.instant();
        states.entrySet().removeIf(e -> e.getValue().isIdle(now, window));
    }

    private static final class State {
        private int failures;
        private Instant windowStart;
        private Instant lockedUntil;

        /** No lockout in force and the failure window is over, so forgetting this key loses nothing. */
        private synchronized boolean isIdle(Instant now, Duration window) {
            boolean unlocked = lockedUntil == null || !now.isBefore(lockedUntil);
            boolean windowOver = windowStart == null || now.isAfter(windowStart.plus(window));
            return unlocked && windowOver;
        }
    }
}
