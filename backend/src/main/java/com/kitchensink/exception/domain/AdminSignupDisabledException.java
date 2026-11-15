package com.kitchensink.exception.domain;


import com.kitchensink.exception.ErrorCode;

/** Admin sign-up is not configured on this server; surfaced as a plain 404 so it does not advertise itself. */
public class AdminSignupDisabledException extends NotFoundException {
    public AdminSignupDisabledException() {
        super(ErrorCode.NOT_FOUND, "Not found.");
    }
}
