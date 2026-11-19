package com.kitchensink.member.model;

public enum Role {
    ADMIN("Admin"),
    VIEWER("Viewer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Display text for dropdowns. */
    public String label() {
        return label;
    }
}
