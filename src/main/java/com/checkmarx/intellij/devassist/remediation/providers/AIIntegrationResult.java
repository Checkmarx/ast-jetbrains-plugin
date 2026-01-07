package com.checkmarx.intellij.devassist.remediation.providers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of an AI integration operation.
 * 
 * <p>
 * This class encapsulates the outcome of sending a prompt to an AI provider,
 * including success/failure status, user-facing messages, and any exceptions.
 */
public final class AIIntegrationResult {

    /**
     * Possible outcomes of an AI integration operation.
     */
    public enum OperationResult {
        /** Full automation succeeded - prompt was sent to AI assistant */
        FULL_SUCCESS,
        /** Partial success - AI opened but automation may have issues */
        PARTIAL_SUCCESS,
        /** AI provider not available - prompt copied to clipboard only */
        PROVIDER_NOT_AVAILABLE,
        /** No providers configured or detected */
        NO_PROVIDER,
        /** Operation failed completely */
        FAILED
    }

    private final OperationResult result;
    private final String message;
    private final String providerName;
    private final @Nullable Exception exception;

    private AIIntegrationResult(OperationResult result, String message,
            String providerName, @Nullable Exception exception) {
        this.result = result;
        this.message = message;
        this.providerName = providerName;
        this.exception = exception;
    }

    // ==================== Getters ====================

    @NotNull
    public OperationResult getResult() {
        return result;
    }

    @NotNull
    public String getMessage() {
        return message;
    }

    @NotNull
    public String getProviderName() {
        return providerName;
    }

    @Nullable
    public Exception getException() {
        return exception;
    }

    /**
     * Returns true if the operation was at least partially successful.
     */
    public boolean isSuccess() {
        return result == OperationResult.FULL_SUCCESS || result == OperationResult.PARTIAL_SUCCESS;
    }

    // ==================== Factory Methods ====================

    public static AIIntegrationResult fullSuccess(String providerName, String message) {
        return new AIIntegrationResult(OperationResult.FULL_SUCCESS, message, providerName, null);
    }

    public static AIIntegrationResult partialSuccess(String providerName, String message) {
        return new AIIntegrationResult(OperationResult.PARTIAL_SUCCESS, message, providerName, null);
    }

    public static AIIntegrationResult providerNotAvailable(String providerName, String message) {
        return new AIIntegrationResult(OperationResult.PROVIDER_NOT_AVAILABLE, message, providerName, null);
    }

    public static AIIntegrationResult noProvider(String message) {
        return new AIIntegrationResult(OperationResult.NO_PROVIDER, message, "None", null);
    }

    public static AIIntegrationResult failed(String providerName, String message, @Nullable Exception e) {
        return new AIIntegrationResult(OperationResult.FAILED, message, providerName, e);
    }

    @Override
    public String toString() {
        return "AIIntegrationResult{" +
                "result=" + result +
                ", provider='" + providerName + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
