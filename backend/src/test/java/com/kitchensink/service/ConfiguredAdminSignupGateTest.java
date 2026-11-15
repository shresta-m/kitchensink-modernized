package com.kitchensink.service;




import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kitchensink.config.AppProperties;
import com.kitchensink.exception.domain.AdminSignupDisabledException;
import com.kitchensink.exception.domain.InvalidAdminCodeException;
import com.kitchensink.service.impl.ConfiguredAdminSignupGate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguredAdminSignupGateTest {

    private static ConfiguredAdminSignupGate gate(String code) {
        return new ConfiguredAdminSignupGate(new AppProperties(
                new AppProperties.Security("x".repeat(32), "test", Duration.ofMinutes(5), false,
                        List.of("http://localhost"), 10, code),
                new AppProperties.BulkImport(10, 1, 100, 10),
                new AppProperties.Bootstrap(null, null, null, null)));
    }

    @Test
    void correctCodePasses() {
        assertThatCode(() -> gate("s3cret-code").verify("s3cret-code")).doesNotThrowAnyException();
    }

    @Test
    void wrongOrMissingCodeIsRejected() {
        assertThatThrownBy(() -> gate("s3cret-code").verify("nope")).isInstanceOf(InvalidAdminCodeException.class);
        assertThatThrownBy(() -> gate("s3cret-code").verify(null)).isInstanceOf(InvalidAdminCodeException.class);
    }

    @Test
    void notConfiguredMeansDisabledEvenForAnEmptyGuess() {
        assertThatThrownBy(() -> gate("").verify("")).isInstanceOf(AdminSignupDisabledException.class);
        assertThatThrownBy(() -> gate(null).verify("x")).isInstanceOf(AdminSignupDisabledException.class);
    }
}
