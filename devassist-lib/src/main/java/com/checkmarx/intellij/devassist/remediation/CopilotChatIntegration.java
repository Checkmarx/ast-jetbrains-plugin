package com.checkmarx.intellij.devassist.remediation;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ChatIntegration} adapter over the existing static {@link CopilotIntegration} utility.
 */
final class CopilotChatIntegration implements ChatIntegration {

    @Override
    public boolean isAvailable(@Nullable Project project) {
        return CopilotIntegration.isCopilotAvailable(project);
    }

    @Override
    public @NotNull ChatIntegrationResult openWithPrompt(@NotNull String prompt, @NotNull Project project) {
        CopilotIntegration.IntegrationResult result =
                CopilotIntegration.openCopilotWithPromptDetailed(prompt, project, null);
        return result.isSuccess()
                ? ChatIntegrationResult.success(result.getMessage())
                : ChatIntegrationResult.notAvailable(result.getMessage());
    }
}
