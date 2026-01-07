package com.checkmarx.intellij.devassist.remediation.providers;

import com.checkmarx.intellij.Utils;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for orchestrating AI integration operations.
 * 
 * <p>
 * This service provides the main entry point for sending fix prompts to AI
 * assistants.
 * It handles:
 * <ul>
 * <li>Provider selection based on user settings</li>
 * <li>Fallback to clipboard when no provider is available</li>
 * <li>Notification handling for success/failure states</li>
 * </ul>
 */
public final class AIIntegrationService {

    private static final Logger LOGGER = Utils.getLogger(AIIntegrationService.class);
    private static final AIIntegrationService INSTANCE = new AIIntegrationService();

    private final AIProviderRegistry registry;

    private AIIntegrationService() {
        this.registry = AIProviderRegistry.getInstance();
    }

    /**
     * Returns the singleton instance of the service.
     */
    public static AIIntegrationService getInstance() {
        return INSTANCE;
    }

    /**
     * Sends a fix prompt to the preferred AI provider.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Copies the prompt to clipboard (guaranteed fallback)</li>
     * <li>Determines the preferred AI provider</li>
     * <li>Sends the prompt via the provider's integration</li>
     * <li>Notifies via callback when complete</li>
     * </ol>
     * 
     * @param prompt            the fix prompt to send
     * @param notificationTitle title for any notifications shown
     * @param successMessage    message to show on full success
     * @param fallbackMessage   message to show when no provider available
     * @param project           the project context
     * @param callback          optional callback for completion notification
     * @return true if operation was initiated successfully
     */
    public boolean fixWithAI(
            @NotNull String prompt,
            @NotNull String notificationTitle,
            @NotNull String successMessage,
            @NotNull String fallbackMessage,
            @NotNull Project project,
            @Nullable Consumer<AIIntegrationResult> callback) {

        LOGGER.info("AI Integration: Starting fix with AI workflow");

        // Step 1: Always copy to clipboard first (guaranteed fallback)
        if (!copyToClipboard(prompt)) {
            AIIntegrationResult result = AIIntegrationResult.failed("None",
                    "Failed to copy prompt to clipboard", null);
            notifyCallback(callback, result);
            showNotification(notificationTitle, result.getMessage(), NotificationType.ERROR, project);
            return false;
        }
        LOGGER.info("AI Integration: Prompt copied to clipboard");

        // Step 2: Find preferred provider
        Optional<AIProvider> providerOpt = registry.getPreferredProvider(project);

        if (providerOpt.isEmpty()) {
            LOGGER.info("AI Integration: No AI provider available, prompt in clipboard");
            AIIntegrationResult result = AIIntegrationResult.noProvider(fallbackMessage);
            notifyCallback(callback, result);
            showNotification(notificationTitle, fallbackMessage, NotificationType.WARNING, project);
            return false;
        }

        // Step 3: Send prompt via provider
        AIProvider provider = providerOpt.get();
        LOGGER.info("AI Integration: Using provider '" + provider.getDisplayName() + "'");

        try {
            AIIntegrationResult result = provider.sendPrompt(prompt, project, detailedResult -> {
                // Handle async completion
                handleProviderResult(detailedResult, notificationTitle, successMessage, project);
                notifyCallback(callback, detailedResult);
            });

            // Handle immediate result
            if (result.isSuccess()) {
                LOGGER.info("AI Integration: Provider initiated successfully");
            } else {
                LOGGER.warn("AI Integration: Provider returned: " + result.getMessage());
            }

            return result.isSuccess();

        } catch (Exception e) {
            LOGGER.warn("AI Integration: Error during provider execution", e);
            AIIntegrationResult result = AIIntegrationResult.failed(provider.getDisplayName(),
                    "Error during AI integration: " + e.getMessage(), e);
            notifyCallback(callback, result);
            showNotification(notificationTitle,
                    "AI integration failed. Prompt is in clipboard.",
                    NotificationType.ERROR, project);
            return false;
        }
    }

    /**
     * Simplified version without callback.
     */
    public boolean fixWithAI(
            @NotNull String prompt,
            @NotNull String notificationTitle,
            @NotNull String successMessage,
            @NotNull String fallbackMessage,
            @NotNull Project project) {
        return fixWithAI(prompt, notificationTitle, successMessage, fallbackMessage, project, null);
    }

    /**
     * Handles the result from a provider and shows appropriate notification.
     */
    private void handleProviderResult(AIIntegrationResult result, String notificationTitle,
            String successMessage, Project project) {
        String message;
        NotificationType notificationType;

        switch (result.getResult()) {
            case FULL_SUCCESS:
                message = "Sending fix prompt to " + result.getProviderName() + "...";
                notificationType = NotificationType.INFORMATION;
                break;
            case PARTIAL_SUCCESS:
                message = result.getMessage();
                notificationType = NotificationType.INFORMATION;
                break;
            case PROVIDER_NOT_AVAILABLE:
            case NO_PROVIDER:
                message = result.getMessage();
                notificationType = NotificationType.WARNING;
                break;
            default:
                message = result.getMessage();
                notificationType = NotificationType.ERROR;
                break;
        }

        showNotification(notificationTitle, message, notificationType, project);
    }

    /**
     * Copies text to the system clipboard.
     */
    private boolean copyToClipboard(@NotNull String text) {
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            return true;
        } catch (Exception e) {
            LOGGER.warn("AI Integration: Failed to copy to clipboard", e);
            return false;
        }
    }

    /**
     * Shows a notification to the user.
     */
    private void showNotification(String title, String content,
            NotificationType type, Project project) {
        ApplicationManager.getApplication().invokeLater(() -> Utils.showNotification(title, content, type, project));
    }

    /**
     * Safely notifies the callback on the EDT.
     */
    private void notifyCallback(@Nullable Consumer<AIIntegrationResult> callback,
            AIIntegrationResult result) {
        if (callback != null) {
            ApplicationManager.getApplication().invokeLater(() -> callback.accept(result));
        }
    }
}
