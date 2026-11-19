package com.kitchensink.security.throttle;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the brute-force budgets. Every number can be tuned from the environment without a rebuild. */
@Configuration
public class ThrottleConfig {

    private static final int MAX_TRACKED_KEYS = 100_000;

    @Bean
    AuthThrottle authThrottle(Clock clock,
                              @Value("${app.security.login.max-failures:5}") int accountFailures,
                              @Value("${app.security.login.window:15m}") Duration window,
                              @Value("${app.security.login.lockout:15m}") Duration lockout,
                              @Value("${app.security.ip.max-failures:50}") int ipFailures) {
        return new AuthThrottle(
                new InMemoryAttemptLimiter(clock, accountFailures, window, lockout, MAX_TRACKED_KEYS),
                new InMemoryAttemptLimiter(clock, ipFailures, window, lockout, MAX_TRACKED_KEYS));
    }
}
