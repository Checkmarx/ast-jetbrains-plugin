package com.checkmarx.intellij.devassist.remediation.providers;

import com.checkmarx.intellij.Utils;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;

/**
 * AI Provider stub for Claude Code (Anthropic).
 * 
 * <p>
 * This provider will integrate with Claude Code when available.
 * Currently implements detection logic only, with prompt sending
 * falling back to clipboard copy.
 * 
 * <p>
 * TODO: Implement full automation when Claude Code provides API or
 * stable UI components for automation.
 */
public class ClaudeCodeProvider implements AIProvider {

    private static final Logger LOGGER = Utils.getLogger(ClaudeCodeProvider.class);

    public static final String PROVIDER_ID = "claude";
    public static final String DISPLAY_NAME = "Claude Code";

    /** Known Claude Code tool window IDs (to be confirmed) */
    private static final String[] CLAUDE_TOOL_WINDOW_IDS = {
            "Claude",
            "Claude Code",
            "Anthropic Claude"
    };

    /** Known Claude Code action IDs (to be confirmed) */
    private static final String[] CLAUDE_ACTION_IDS = {
            "claude.chat.show",
            "Claude.Chat.Show",
            "claude.openChat"
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
            for (String toolWindowId : CLAUDE_TOOL_WINDOW_IDS) {
                ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
                if (toolWindow != null) {
                    LOGGER.debug("ClaudeCodeProvider: Found tool window '" + toolWindowId + "'");
                    return true;
                }
            }
        }

        // Check for actions
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : CLAUDE_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                LOGGER.debug("ClaudeCodeProvider: Found action '" + actionId + "'");
                return true;
            }
        }

        LOGGER.debug("ClaudeCodeProvider: Claude Code not detected");
        return false;
    }

    @NotNull
    @Override
    public AIIntegrationResult sendPrompt(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<AIIntegrationResult> callback) {

        LOGGER.info("ClaudeCodeProvider: Claude Code support coming soon");

        // Claude Code integration is coming soon
        // For now, copy to clipboard and show coming soon message
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(prompt));

            AIIntegrationResult result = AIIntegrationResult.partialSuccess(DISPLAY_NAME,
                    "Claude Code support coming soon! Fix prompt copied to clipboard. " +
                            "Please install GitHub Copilot and paste the prompt manually, or select GitHub Copilot from Settings.");

            if (callback != null) {
                callback.accept(result);
            }
            return result;

        } catch (Exception e) {
            LOGGER.warn("ClaudeCodeProvider: Error during prompt copying", e);
            AIIntegrationResult result = AIIntegrationResult.failed(DISPLAY_NAME,
                    "Error: " + e.getMessage(), e);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        }
    }

    @Override
    public int getPriority() {
        // Third priority after Copilot and Augment
        return 300;
    }

    /**
     * Attempts to open the Claude Code tool window.
     */
    private boolean tryOpenClaudeWindow(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : CLAUDE_TOOL_WINDOW_IDS) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                toolWindow.show();
                return true;
            }
        }
        return false;
    }
}
