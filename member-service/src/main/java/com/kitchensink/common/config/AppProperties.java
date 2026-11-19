package com.kitchensink.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param adminCode      code that grants ADMIN at self-registration; empty means nobody can register as admin
 * @param jwt            token signing settings
 * @param allowedOrigins browser origins allowed by CORS
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String adminCode, @DefaultValue JwtSettings jwt,
                            @DefaultValue("http://localhost:5173") List<String> allowedOrigins) {

    /** @param secret HMAC signing secret, at least 32 characters; @param ttl how long a token stays valid */
    public record JwtSettings(String secret, @DefaultValue("30m") Duration ttl) {
    }
}
