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
 * AI Provider stub for Augment Code.
 * 
 * <p>
 * This provider will integrate with Augment Code when available.
 * Currently implements detection logic only, with prompt sending
 * falling back to clipboard copy.
 * 
 * <p>
 * TODO: Implement full automation when Augment provides API or
 * stable UI components for automation.
 */
public class AugmentProvider implements AIProvider {

    private static final Logger LOGGER = Utils.getLogger(AugmentProvider.class);

    public static final String PROVIDER_ID = "augment";
    public static final String DISPLAY_NAME = "Augment Code";

    /** Known Augment tool window IDs (to be confirmed) */
    private static final String[] AUGMENT_TOOL_WINDOW_IDS = {
            "Augment",
            "Augment Code",
            "Augment AI"
    };

    /** Known Augment action IDs (to be confirmed) */
    private static final String[] AUGMENT_ACTION_IDS = {
            "augment.chat.show",
            "Augment.Chat.Show",
            "augment.openChat"
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
            for (String toolWindowId : AUGMENT_TOOL_WINDOW_IDS) {
                ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
                if (toolWindow != null) {
                    LOGGER.debug("AugmentProvider: Found tool window '" + toolWindowId + "'");
                    return true;
                }
            }
        }

        // Check for actions
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : AUGMENT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                LOGGER.debug("AugmentProvider: Found action '" + actionId + "'");
                return true;
            }
        }

        LOGGER.debug("AugmentProvider: Augment not detected");
        return false;
    }

    @NotNull
    @Override
    public AIIntegrationResult sendPrompt(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<AIIntegrationResult> callback) {

        LOGGER.info("AugmentProvider: Attempting to send prompt to Augment");

        // TODO: Implement Augment-specific automation when available
        // For now, copy to clipboard and return partial success
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(prompt));

            // Try to open Augment tool window
            boolean opened = tryOpenAugmentWindow(project);

            AIIntegrationResult result;
            if (opened) {
                result = AIIntegrationResult.partialSuccess(DISPLAY_NAME,
                        "Augment Code opened. Prompt copied to clipboard - please paste manually (Ctrl/Cmd+V).");
            } else {
                result = AIIntegrationResult.providerNotAvailable(DISPLAY_NAME,
                        "Could not open Augment Code. Prompt copied to clipboard.");
            }

            if (callback != null) {
                callback.accept(result);
            }
            return result;

        } catch (Exception e) {
            LOGGER.warn("AugmentProvider: Error during prompt sending", e);
            AIIntegrationResult result = AIIntegrationResult.failed(DISPLAY_NAME,
                    "Error sending to Augment: " + e.getMessage(), e);
            if (callback != null) {
                callback.accept(result);
            }
            return result;
        }
    }

    @Override
    public int getPriority() {
        // Second priority after Copilot
        return 200;
    }

    /**
     * Attempts to open the Augment tool window.
     */
    private boolean tryOpenAugmentWindow(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : AUGMENT_TOOL_WINDOW_IDS) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                toolWindow.show();
                return true;
            }
        }
        return false;
    }
}
