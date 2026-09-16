package com.checkmarx.intellij.devassist.remediation;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ChatIntegration} adapter over the existing static {@link AiAssistantIntegration} utility.
 */
final class AiAssistantChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return AiAssistantIntegration.isAiAssistantAvailable(project);
    }

    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project) {
        AiAssistantIntegration.IntegrationResult result =
                AiAssistantIntegration.openAiAssistantWithPromptDetailed(prompt, project);
        return result.isSuccess()
                ? ChatIntegrationResult.success(result.getMessage())
                : ChatIntegrationResult.notAvailable(result.getMessage());
    }
}
