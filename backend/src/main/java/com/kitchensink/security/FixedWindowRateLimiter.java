package com.kitchensink.security;



import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counter per key: at most {@code limit} calls per {@code windowSeconds}. In-memory, so it is
 * per instance; behind several instances, move the counter to a shared store (e.g. Redis) or the gateway.
 */
public class FixedWindowRateLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private record Window(long id, int count) {}

    private final int limit;
    private final long windowSeconds;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, long windowSeconds, Clock clock) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.clock = clock;
    }

    /** @return true if this call is allowed, false if the key is over its limit for the current window */
    public boolean tryAcquire(String key) {
        long now = clock.instant().getEpochSecond();
        long windowId = now / windowSeconds;
        if (windows.size() > PURGE_THRESHOLD) {
            windows.values().removeIf(w -> w.id() < windowId);
        }
        Window w = windows.merge(key, new Window(windowId, 1),
                (old, fresh) -> old.id() == windowId ? new Window(windowId, old.count() + 1) : fresh);
        return w.count() <= limit;
    }

    public long retryAfterSeconds() {
        long now = clock.instant().getEpochSecond();
        return windowSeconds - (now % windowSeconds);
    }
}
