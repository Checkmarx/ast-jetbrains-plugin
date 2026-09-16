package com.checkmarx.intellij.devassist.remediation;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single AI chat target (Copilot, JetBrains AI Assistant, ...) that {@link RemediationManager}
 * can send a generated fix/explanation prompt to.
 * <p>
 * Implementations are looked up via {@link AiAgent#chatIntegration()} - adding a new agent means
 * writing one implementation of this interface and wiring it into a new {@link AiAgent} constant,
 * with no changes required in {@link RemediationManager} itself.
 */
public interface ChatIntegration {

    /**
     * Whether this agent's chat is installed/available for the given project. {@code project}
     * may be {@code null} for a global (non-project-scoped) check.
     */
    boolean isAvailable(@Nullable Project project);

    /**
     * Opens this agent's chat and attempts to deliver {@code prompt} to it (best-effort - some
     * agents can auto-submit, others may only manage to paste it for the user to send).
     */
    @NotNull
    ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project);
}
