package com.kitchensink.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.kitchensink.TestData;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AppConfigTest {

    private final AppConfig config = new AppConfig();

    @Test
    void clockIsUtc() {
        Clock clock = config.clock();
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void passwordEncoderHashesAndVerifies() {
        var encoder = config.passwordEncoder();
        String hash = encoder.encode("Secret@123");

        assertThat(hash).isNotEqualTo("Secret@123").startsWith("$2a$" + AppConfig.BCRYPT_STRENGTH + "$");
        assertThat(encoder.matches("Secret@123", hash)).isTrue();
        assertThat(encoder.matches("Other1234", hash)).isFalse();
    }

    @Test
    void theUnknownEmailDummyHashCostsTheSameAsARealOne() throws Exception {
        var field = com.kitchensink.auth.service.impl.AuthServiceImpl.class.getDeclaredField("DUMMY_HASH");
        field.setAccessible(true);

        assertThat((String) field.get(null)).startsWith("$2a$" + AppConfig.BCRYPT_STRENGTH + "$");
    }

    @Test
    void propertiesHoldTheAdminCode() {
        AppProperties props = com.kitchensink.TestData.props("CODE");
        assertThat(props.adminCode()).isEqualTo("CODE");
        assertThat(props.jwt().ttl()).isEqualTo(java.time.Duration.ofMinutes(30));
        assertThat(props.allowedOrigins()).containsExactly("http://localhost:5173");
    }
}
