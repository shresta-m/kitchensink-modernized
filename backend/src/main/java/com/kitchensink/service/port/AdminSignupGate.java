package com.kitchensink.service.port;


/** Decides whether a caller may create an administrator through public sign-up. */
public interface AdminSignupGate {

    /**
     * @throws com.kitchensink.exception.domain.AdminSignupDisabledException if no code is configured
     * @throws com.kitchensink.exception.domain.InvalidAdminCodeException if the code is wrong
     */
    void verify(String suppliedCode);
}
