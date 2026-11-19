package com.kitchensink.security.throttle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.TestData;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ThrottleConfigTest {

    private AuthThrottle build(int accountFailures) {
        return new ThrottleConfig().authThrottle(TestData.CLOCK, accountFailures, Duration.ofMinutes(15),
                Duration.ofMinutes(15), 50);
    }

    @Test
    void buildsAThrottleFromTheConfiguredNumbers() {
        AuthThrottle throttle = build(2);

        throttle.loginFailed("a@example.com", "1.1.1.1");
        assertThatCode(() -> throttle.beforeLogin("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
        throttle.loginFailed("a@example.com", "1.1.1.1");

        assertThatThrownBy(() -> throttle.beforeLogin("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void wrongAdminCodesUseTheSameBudgetAsLogins() {
        AuthThrottle throttle = build(2);
        throttle.adminCodeFailed("10.0.0.1");
        throttle.adminCodeFailed("10.0.0.1");

        assertThatThrownBy(() -> throttle.beforeAdminCode("10.0.0.1")).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.beforeAdminCode("10.0.0.2")).doesNotThrowAnyException();
    }
}
