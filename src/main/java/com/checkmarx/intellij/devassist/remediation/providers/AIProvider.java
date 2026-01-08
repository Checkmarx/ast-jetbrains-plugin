package com.checkmarx.intellij.devassist.remediation.providers;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Interface for AI assistant providers that can receive and process fix
 * prompts.
 * 
 * <p>
 * Implementations of this interface provide integration with AI assistants
 * like GitHub Copilot. Each provider handles the specifics of opening the
 * AI assistant, sending prompts, and managing the interaction.
 * 
 * <p>
 * The provider system supports:
 * <ul>
 * <li>Detection of whether the AI assistant is available</li>
 * <li>Priority-based provider selection</li>
 * <li>Async prompt sending with callback support</li>
 * </ul>
 */
public interface AIProvider {

    /**
     * Returns the unique identifier for this provider.
     * Used for configuration and persistence.
     * 
     * @return provider identifier (e.g., "copilot")
     */
    @NotNull
    String getId();

    /**
     * Returns the human-readable display name for this provider.
     * Used in UI elements and notifications.
     * 
     * @return display name (e.g., "GitHub Copilot")
     */
    @NotNull
    String getDisplayName();

    /**
     * Checks if this AI provider is currently available in the IDE.
     * This typically involves checking if the required plugin is installed and
     * active.
     * 
     * @param project the project context (can be null for global check)
     * @return true if the provider is available and ready to use
     */
    boolean isAvailable(@Nullable Project project);

    /**
     * Sends a prompt to the AI assistant.
     * 
     * <p>
     * This method should:
     * <ol>
     * <li>Copy the prompt to clipboard as fallback</li>
     * <li>Open the AI assistant window</li>
     * <li>Paste and send the prompt if possible</li>
     * <li>Notify via callback when complete</li>
     * </ol>
     * 
     * @param prompt   the fix prompt to send
     * @param project  the project context
     * @param callback optional callback for async completion notification
     * @return immediate result indicating if operation was initiated
     */
    @NotNull
    AIIntegrationResult sendPrompt(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<AIIntegrationResult> callback);

    /**
     * Returns the priority of this provider.
     * Lower values indicate higher priority.
     * Used when auto-selecting a provider.
     * 
     * @return priority value (lower = higher priority)
     */
    default int getPriority() {
        return 500;
    }
}
