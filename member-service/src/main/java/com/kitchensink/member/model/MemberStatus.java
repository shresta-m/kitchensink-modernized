package com.kitchensink.member.model;

/**
 * Account state shown in the members table. It is derived by {@link Member#getStatus()} from the member's flags, so the
 * rest of the code asks the member instead of combining flags itself.
 */
public enum MemberStatus {
    ACTIVE("Active"),
    TEMP_PASSWORD("Temp password"),
    PENDING_APPROVAL("Pending approval");

    private final String label;

    MemberStatus(String label) {
        this.label = label;
    }

    /** Display text for dropdowns. */
    public String label() {
        return label;
    }
}
