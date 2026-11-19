package com.kitchensink.security.throttle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.TestData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthThrottleTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private AuthThrottle throttle;

    @BeforeEach
    void setUp() {
        // account budget 3, ip budget 5
        throttle = new AuthThrottle(
                new InMemoryAttemptLimiter(TestData.CLOCK, 3, WINDOW, WINDOW, 100),
                new InMemoryAttemptLimiter(TestData.CLOCK, 5, WINDOW, WINDOW, 100));
    }

    private void failLogins(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.loginFailed(email, ip);
        }
    }

    @Test
    void anEmailIsLockedAfterItsFailuresNoMatterWhichAddressTheyCameFrom() {
        throttle.loginFailed("a@example.com", "1.1.1.1");
        throttle.loginFailed("a@example.com", "2.2.2.2");
        throttle.loginFailed("a@example.com", "3.3.3.3");

        assertThatThrownBy(() -> throttle.beforeLogin("a@example.com", "4.4.4.4"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.beforeLogin("b@example.com", "4.4.4.4")).doesNotThrowAnyException();
    }

    @Test
    void anAddressIsLockedAfterItsFailuresAcrossDifferentEmails() {
        for (int i = 0; i < 5; i++) {
            throttle.loginFailed("user" + i + "@example.com", "9.9.9.9");
        }

        assertThatThrownBy(() -> throttle.beforeLogin("fresh@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.beforeLogin("fresh@example.com", "8.8.8.8")).doesNotThrowAnyException();
    }

    @Test
    void aGoodLoginClearsTheAccountBudgetButNotTheAddressBudget() {
        failLogins("a@example.com", "9.9.9.9", 2);
        throttle.loginSucceeded("a@example.com");

        failLogins("a@example.com", "9.9.9.9", 2);                      // would have been 4 account failures without the reset
        assertThatCode(() -> throttle.beforeLogin("a@example.com", "7.7.7.7")).doesNotThrowAnyException();

        throttle.loginFailed("other@example.com", "9.9.9.9");           // the address has now failed 5 times in total
        assertThatThrownBy(() -> throttle.beforeLogin("fresh@example.com", "9.9.9.9"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void passwordChangeAttemptsAreLimitedPerMemberAndSeparateFromLogins() {
        throttle.passwordChangeFailed("m1");
        throttle.passwordChangeFailed("m1");
        throttle.passwordChangeFailed("m1");

        assertThatThrownBy(() -> throttle.beforePasswordChange("m1")).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.beforePasswordChange("m2")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.beforeLogin("m1", "1.1.1.1")).doesNotThrowAnyException();

        throttle.passwordChangeSucceeded("m1");
        assertThatCode(() -> throttle.beforePasswordChange("m1")).doesNotThrowAnyException();
    }

    @Test
    void wrongAdminCodesLockTheAddressAndNothingElse() {
        throttle.adminCodeFailed("1.1.1.1");
        throttle.adminCodeFailed("1.1.1.1");
        throttle.adminCodeFailed("1.1.1.1");

        assertThatThrownBy(() -> throttle.beforeAdminCode("1.1.1.1")).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> throttle.beforeAdminCode("2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.beforeLogin("a@example.com", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void anOversizedKeyIsCappedAndANullAddressIsTolerated() {
        String huge = "x".repeat(10_000) + "@example.com";

        assertThatCode(() -> {
            throttle.loginFailed(huge, null);
            throttle.beforeLogin(huge, null);
        }).doesNotThrowAnyException();
        assertThat(huge.length()).isGreaterThan(320);                   // the key stored was cut to 320 characters
    }
}
