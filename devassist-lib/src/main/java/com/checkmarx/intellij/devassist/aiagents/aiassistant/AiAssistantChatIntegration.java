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
public final class AiAssistantChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return AiAssistantIntegration.isAiAssistantAvailable(project);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * AiAssistantIntegration returns its result synchronously (either success or failure) and
     * doesn't provide an async completion signal, so this adapter invokes the callback
     * synchronously with that result to match the contract of other ChatIntegration implementations.
     */
    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project,
                                                         @Nullable Consumer<ChatIntegrationResult> onFinalResult) {
        ChatIntegrationResult result = AiAssistantIntegration.openAiAssistantWithPromptDetailed(prompt, project, null);
        if (onFinalResult != null) {
            onFinalResult.accept(result);
        }
        return result;
    }
}
