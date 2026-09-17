package com.checkmarx.intellij.devassist.remediation;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@link ChatIntegration} adapter over the existing static {@link CopilotIntegration} utility.
 */
final class CopilotChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return CopilotIntegration.isCopilotAvailable(project);
    }

    /**
     * Copilot's chat-window-open call returns immediately, but whether the fix prompt actually
     * gets pasted/sent is only known once the background UI-automation sequence in
     * {@link CopilotIntegration} finishes. That final outcome is forwarded to
     * {@code onFinalResult} as-is - it is not assumed to be a success just because the chat
     * window itself opened.
     */
    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project,
                                                          @Nullable Consumer<ChatIntegrationResult> onFinalResult) {
        CopilotIntegration.IntegrationResult result = CopilotIntegration.openCopilotWithPromptDetailed(
                prompt, project, finalResult -> {
                    if (onFinalResult != null) {
                        onFinalResult.accept(toChatIntegrationResult(finalResult));
                    }
                });
        return toChatIntegrationResult(result);
    }

    private static ChatIntegrationResult toChatIntegrationResult(CopilotIntegration.IntegrationResult result) {
        return result.isSuccess()
                ? ChatIntegrationResult.success(result.getMessage())
                : ChatIntegrationResult.notAvailable(result.getMessage());
    }
}
