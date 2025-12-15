package com.checkmarx.intellij.util;

import com.checkmarx.intellij.Constants;
import lombok.Getter;

/**
 * Enum representing various levels of severity.
 * <p>
 * Each severity level is associated with a specific string value. The levels defined are:
 * LOW, MEDIUM, HIGH, CRITICAL, MALICIOUS, UNKNOWN, and OK.
 * These levels are generally used to categorize the severity of certain events, conditions, or states.
 */
@Getter
public enum SeverityLevel {

    MALICIOUS(Constants.MALICIOUS_SEVERITY, 1),
    CRITICAL(Constants.CRITICAL_SEVERITY, 2),
    HIGH(Constants.HIGH_SEVERITY, 3),
    MEDIUM(Constants.MEDIUM_SEVERITY, 4),
    LOW(Constants.LOW_SEVERITY, 5),
    UNKNOWN(Constants.UNKNOWN, 6),
    OK(Constants.OK, 7);

    private final String severity;
    private final int precedence;

    SeverityLevel(String severity, int precedence) {
        this.severity = severity;
        this.precedence = precedence;
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
