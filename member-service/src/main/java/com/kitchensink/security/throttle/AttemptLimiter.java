package com.kitchensink.security.throttle;

/**
 * Counts failed attempts per key and locks the key out once there are too many. This is the swap seam: the bundled
 * {@link InMemoryAttemptLimiter} is per instance, so a multi-instance deployment replaces it with a shared store
 * (Redis, or a Mongo collection with a TTL index) or moves the rule to the API gateway. Callers do not change.
 */
public interface AttemptLimiter {

    /** Throws {@link TooManyAttemptsException} while the key is locked out. Does not count as an attempt. */
    void checkAllowed(String key);

    /** Counts one failed attempt. The failure that reaches the limit starts the lockout. */
    void recordFailure(String key);

    /** Forgets the key: a correct credential clears its failures and its lockout history. */
    void recordSuccess(String key);
}
