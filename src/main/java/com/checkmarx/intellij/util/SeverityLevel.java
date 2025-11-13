package com.checkmarx.intellij.util;

import com.checkmarx.intellij.Constants;
import lombok.Getter;

/**
 * Enum representing various levels of severity.
 *
 * Each severity level is associated with a specific string value. The levels defined are:
 * LOW, MEDIUM, HIGH, CRITICAL, MALICIOUS, UNKNOWN, and OK.
 * These levels are generally used to categorize the severity of certain events, conditions, or states.
 */
@Getter
public enum SeverityLevel {

    LOW(Constants.LOW_SEVERITY),
    MEDIUM(Constants.MEDIUM_SEVERITY),
    HIGH(Constants.HIGH_SEVERITY),
    CRITICAL(Constants.CRITICAL_SEVERITY),
    MALICIOUS(Constants.MALICIOUS_SEVERITY),
    UNKNOWN(Constants.UNKNOWN),
    OK(Constants.OK);

    private final String severity;

    SeverityLevel(String severity) {
        this.severity = severity;
    }

    /**
     * Returns the corresponding {@code SeverityLevel} for the given string value.
     * If no match is found, the method returns {@code UNKNOWN}.
     *
     * @param value the string representation of the severity level to be matched
     * @return the matching {@code SeverityLevel}, or {@code UNKNOWN} if no match is found
     */
    public static SeverityLevel fromValue(String value) {
        for (SeverityLevel level : values()) {
            if (level.getSeverity().equalsIgnoreCase(value)) {
                return level;
            }
        }
        return UNKNOWN;
    }
}
