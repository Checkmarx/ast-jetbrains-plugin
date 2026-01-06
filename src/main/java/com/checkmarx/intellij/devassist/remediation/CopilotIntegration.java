package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Utils;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for integrating with GitHub Copilot in IntelliJ IDEA.
 * Provides methods to open Copilot chat and send prompts programmatically.
 */
public final class CopilotIntegration {

    private static final Logger LOGGER = Utils.getLogger(CopilotIntegration.class);

    // Known Copilot action IDs - these may vary by Copilot version
    private static final String[] COPILOT_CHAT_ACTION_IDS = {
            "copilot.chat.show",           // Primary Copilot chat action
            "copilot.openChat",            // Alternative action ID
            "GitHub.Copilot.Chat.Show",    // Another possible action ID
            "copilot.chat.openChat"        // Yet another variant
    };

    // Known Copilot tool window IDs
    private static final String[] COPILOT_TOOL_WINDOW_IDS = {
            "GitHub Copilot Chat",
            "Copilot Chat",
            "GitHub Copilot"
    };

    private CopilotIntegration() {
        // Prevent instantiation
    }

    /**
     * Copies the prompt to clipboard and attempts to open GitHub Copilot chat.
     * This provides a streamlined one-click fix experience.
     *
     * @param prompt  The fix prompt to send to Copilot
     * @param project The current project context
     * @return true if the operation was successful, false otherwise
     */
    public static boolean openCopilotWithPrompt(@NotNull String prompt, @NotNull Project project) {
        // First, copy the prompt to clipboard
        copyToClipboard(prompt);

        // Then try to open Copilot chat
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            boolean opened = tryOpenCopilotChat(project);
            if (opened) {
                LOGGER.info("RTS-Fix: Successfully opened Copilot chat with fix prompt");
            } else {
                LOGGER.info("RTS-Fix: Copilot chat not available, prompt copied to clipboard");
            }
            return opened;
        });
    }

    /**
     * Asynchronously opens Copilot chat with the given prompt.
     *
     * @param prompt  The fix prompt to send to Copilot
     * @param project The current project context
     * @return CompletableFuture that resolves to true if successful
     */
    public static CompletableFuture<Boolean> openCopilotWithPromptAsync(@NotNull String prompt, @NotNull Project project) {
        return CompletableFuture.supplyAsync(() -> {
            copyToClipboard(prompt);
            return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> tryOpenCopilotChat(project));
        });
    }

    /**
     * Copies text to the system clipboard.
     *
     * @param text The text to copy
     */
    private static void copyToClipboard(@NotNull String text) {
        ApplicationManager.getApplication().invokeLater(() -> 
            CopyPasteManager.getInstance().setContents(new StringSelection(text))
        );
    }

    /**
     * Attempts to open GitHub Copilot chat using various known action IDs.
     *
     * @param project The current project context
     * @return true if Copilot chat was successfully opened
     */
    private static boolean tryOpenCopilotChat(@NotNull Project project) {
        // First, try to open via tool window
        if (tryOpenCopilotToolWindow(project)) {
            return true;
        }

        // Then try via action
        return tryInvokeCopilotAction(project);
    }

    /**
     * Attempts to open Copilot chat via tool window.
     */
    private static boolean tryOpenCopilotToolWindow(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        
        for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    toolWindow.show();
                    toolWindow.activate(null);
                });
                LOGGER.debug("RTS-Fix: Opened Copilot tool window: " + toolWindowId);
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to invoke Copilot chat action.
     */
    private static boolean tryInvokeCopilotAction(@NotNull Project project) {
        ActionManager actionManager = ActionManager.getInstance();
        
        for (String actionId : COPILOT_CHAT_ACTION_IDS) {
            AnAction action = actionManager.getAction(actionId);
            if (action != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    DataContext dataContext = dataId -> {
                        if (com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.is(dataId)) {
                            return project;
                        }
                        return null;
                    };
                    AnActionEvent event = AnActionEvent.createFromDataContext(
                            "CxOneAssist.FixWithAI", null, dataContext);
                    ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                });
                LOGGER.debug("RTS-Fix: Invoked Copilot action: " + actionId);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if GitHub Copilot is available in the current IDE.
     *
     * @param project The current project context
     * @return true if Copilot is available
     */
    public static boolean isCopilotAvailable(@Nullable Project project) {
        if (project == null) {
            return false;
        }
        
        // Check for tool window
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
            if (toolWindowManager.getToolWindow(toolWindowId) != null) {
                return true;
            }
        }
        
        // Check for action
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : COPILOT_CHAT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                return true;
            }
        }
        
        return false;
    }
}

