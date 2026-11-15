package com.kitchensink.service.impl;



import com.kitchensink.config.AppProperties;
import com.kitchensink.exception.domain.AdminSignupDisabledException;
import com.kitchensink.exception.domain.InvalidAdminCodeException;
import com.kitchensink.service.port.AdminSignupGate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Checks the supplied code against {@code app.security.admin-signup-code} in constant time. */
@Component
public class ConfiguredAdminSignupGate implements AdminSignupGate {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredAdminSignupGate.class);

    private final AppProperties.Security security;

    public ConfiguredAdminSignupGate(AppProperties props) {
        this.security = props.security();
    }

    @Override
    public void verify(String suppliedCode) {
        if (!security.adminSignupEnabled()) {
            throw new AdminSignupDisabledException();
        }
        if (!codeMatches(suppliedCode)) {
            log.warn("Admin sign-up rejected: wrong code");
            throw new InvalidAdminCodeException();
        }
    }

    /** Compares SHA-256 digests, so neither content nor length leaks through timing. */
    private boolean codeMatches(String supplied) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] expected = sha.digest(security.adminSignupCode().getBytes(StandardCharsets.UTF_8));
            byte[] actual = sha.digest((supplied == null ? "" : supplied).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, actual);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
