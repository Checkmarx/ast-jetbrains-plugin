package com.checkmarx.intellij.devassist.remediation.providers;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.remediation.CopilotIntegration;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * AI Provider implementation for GitHub Copilot.
 * 
 * <p>
 * This provider integrates with GitHub Copilot Chat by:
 * <ol>
 * <li>Detecting if Copilot is installed via tool window or action presence</li>
 * <li>Opening the Copilot Chat window</li>
 * <li>Switching to Agent mode for code editing capabilities</li>
 * <li>Pasting and sending the fix prompt automatically</li>
 * </ol>
 * 
 * <p>
 * Since Copilot does not provide a public API, this implementation uses
 * UI automation with reflection to interact with Copilot's internal components.
 */
public class CopilotProvider implements AIProvider {

    private static final Logger LOGGER = Utils.getLogger(CopilotProvider.class);

    public static final String PROVIDER_ID = "copilot";
    public static final String DISPLAY_NAME = "GitHub Copilot";

    /** Known Copilot action IDs for opening the chat window */
    private static final String[] COPILOT_CHAT_ACTION_IDS = {
            "copilot.chat.show",
            "GitHub.Copilot.Chat.Show",
            "copilot.openChat",
            "copilot.chat.openChat"
    };

    /** Known Copilot tool window IDs */
    private static final String[] COPILOT_TOOL_WINDOW_IDS = {
            "GitHub Copilot Chat",
            "Copilot Chat",
            "GitHub Copilot"
    };

    @NotNull
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isAvailable(@Nullable Project project) {
        // Check for tool window
        if (project != null) {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
                ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
                if (toolWindow != null) {
                    LOGGER.debug("CopilotProvider: Found tool window '" + toolWindowId + "'");
                    return true;
                }
            }
        }

        // Check for actions
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : COPILOT_CHAT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                LOGGER.debug("CopilotProvider: Found action '" + actionId + "'");
                return true;
            }
        }

        LOGGER.debug("CopilotProvider: Copilot not detected");
        return false;
    }

    @NotNull
    @Override
    public AIIntegrationResult sendPrompt(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<AIIntegrationResult> callback) {

        LOGGER.info("CopilotProvider: Sending prompt to Copilot");

        // Delegate to existing CopilotIntegration which handles all the automation
        // We wrap the callback to convert from CopilotIntegration.IntegrationResult to
        // AIIntegrationResult
        CopilotIntegration.IntegrationResult legacyResult = CopilotIntegration.openCopilotWithPromptDetailed(prompt,
                project, legacyDetailedResult -> {
                    if (callback != null) {
                        AIIntegrationResult converted = convertResult(legacyDetailedResult);
                        callback.accept(converted);
                    }
                });

        return convertResult(legacyResult);
    }

    @Override
    public int getPriority() {
        // Copilot is the default/highest priority provider
        return 100;
    }

    /**
     * Converts legacy CopilotIntegration result to new AIIntegrationResult.
     */
    private AIIntegrationResult convertResult(CopilotIntegration.IntegrationResult legacyResult) {
        switch (legacyResult.getResult()) {
            case FULL_SUCCESS:
                return AIIntegrationResult.fullSuccess(DISPLAY_NAME, legacyResult.getMessage());
            case PARTIAL_SUCCESS:
                return AIIntegrationResult.partialSuccess(DISPLAY_NAME, legacyResult.getMessage());
            case COPILOT_NOT_AVAILABLE:
                return AIIntegrationResult.providerNotAvailable(DISPLAY_NAME, legacyResult.getMessage());
            case FAILED:
            default:
                return AIIntegrationResult.failed(DISPLAY_NAME, legacyResult.getMessage(),
                        legacyResult.getException());
        }
    }
}
