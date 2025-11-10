package com.checkmarx.intellij.util;

/**
 * Enum for vulnerability statuses.
 */

public enum Status {

    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    CRITICAL("Critical"),
    MALICIOUS("Malicious"),
    UNKNOWN("Unknown"),
    OK("Ok"),
    INFO("Info");

    private final String statusValue;

    Status(String status) {
        this.statusValue = status;
    }

    public String getStatus() {
        return statusValue;
    }

    /**
     * Get the status from the provided value.
     * @param value - status value
     * @return - status enum
     */
    public static Status fromValue(String value) {
        for (Status status : values()) {
            if (status.getStatus().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
