package com.checkmarx.intellij.devassist.remediation;

import lombok.Getter;

/**
 * Outcome of {@link ChatIntegration(String, com.intellij.openapi.project.Project)},
 * common across all agent implementations.
 */
@Getter
public final class ChatIntegrationResult {

    private final boolean success;
    private final String message;

    private ChatIntegrationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ChatIntegrationResult success(String message) {
        return new ChatIntegrationResult(true, message);
    }

    public static ChatIntegrationResult notAvailable(String message) {
        return new ChatIntegrationResult(false, message);
    }
}
