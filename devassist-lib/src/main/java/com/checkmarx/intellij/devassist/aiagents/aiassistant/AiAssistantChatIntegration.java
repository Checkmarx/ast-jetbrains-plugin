package com.checkmarx.intellij.devassist.aiagents.aiassistant;

import com.checkmarx.intellij.devassist.aiagents.ChatIntegration;
import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * {@link ChatIntegration} adapter over the existing static {@link AiAssistantIntegration} utility.
 */
final class AiAssistantChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return AiAssistantIntegration.isAiAssistantAvailable(project);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * {@link AiAssistantIntegration#openAiAssistantWithPromptDetailed} does not currently expose
     * its own async paste/send outcome, so its synchronous "chat opened" result is treated as
     * final and forwarded to {@code onFinalResult} immediately.
     */
    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project,
                                                         @Nullable Consumer<ChatIntegrationResult> onFinalResult) {
        AiAssistantIntegration.IntegrationResult result =
                AiAssistantIntegration.openAiAssistantWithPromptDetailed(prompt, project);
        ChatIntegrationResult chatResult = result.isSuccess()
                ? ChatIntegrationResult.success(result.getMessage())
                : ChatIntegrationResult.notAvailable(result.getMessage());
        if (onFinalResult != null) {
            onFinalResult.accept(chatResult);
        }
        return chatResult;
    }
}
