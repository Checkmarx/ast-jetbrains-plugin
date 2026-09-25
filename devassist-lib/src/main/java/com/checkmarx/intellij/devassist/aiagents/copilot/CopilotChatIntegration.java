package com.checkmarx.intellij.devassist.aiagents.copilot;

import com.checkmarx.intellij.devassist.aiagents.ChatIntegration;
import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@link ChatIntegration} adapter over the existing static {@link CopilotIntegration} utility.
 */
public final class CopilotChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return CopilotIntegration.isCopilotAvailable(project);
    }

    /**
     * Copilot's chat-window-open call returns immediately, but whether the fix prompt actually
     * gets pasted/sent is only known once the background UI-automation sequence in
     * {@link CopilotIntegration} finishes. The completion callback will be invoked with the
     * actual final outcome after automation completes.
     */
    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project,
                                                         @Nullable Consumer<ChatIntegrationResult> onFinalResult) {
        return CopilotIntegration.openCopilotWithPromptDetailed(prompt, project, onFinalResult);
    }
}
