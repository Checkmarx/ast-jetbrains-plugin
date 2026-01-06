package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.Utils;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for integrating with GitHub Copilot in IntelliJ IDEA.
 * Provides methods to open Copilot chat, switch to agent mode, paste prompts, and send automatically.
 */
public final class CopilotIntegration {

    private static final Logger LOGGER = Utils.getLogger(CopilotIntegration.class);

    // Delay constants for UI interactions (in milliseconds)
    private static final int COPILOT_OPEN_DELAY = 800;
    private static final int AGENT_MODE_SWITCH_DELAY = 300;
    private static final int PASTE_DELAY = 200;
    private static final int SEND_DELAY = 100;

    // Known Copilot action IDs - these may vary by Copilot version
    private static final String[] COPILOT_CHAT_ACTION_IDS = {
            "copilot.chat.show",
            "copilot.openChat",
            "GitHub.Copilot.Chat.Show",
            "copilot.chat.openChat"
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
     * Opens Copilot chat, switches to agent mode, pastes the prompt, and sends it automatically.
     * This provides a fully automated one-click fix experience.
     *
     * @param prompt  The fix prompt to send to Copilot
     * @param project The current project context
     * @return true if the operation was initiated successfully, false otherwise
     */
    public static boolean openCopilotWithPrompt(@NotNull String prompt, @NotNull Project project) {
        // Copy the prompt to clipboard first
        copyToClipboardSync(prompt);

        // Try to open Copilot chat
        boolean opened = ApplicationManager.getApplication().runReadAction(
                (Computable<Boolean>) () -> tryOpenCopilotChat(project));

        if (opened) {
            // Schedule the automation sequence after Copilot opens
            scheduleAutomatedPromptEntry(prompt);
            LOGGER.info("RTS-Fix: Initiated automated Copilot fix workflow");
        } else {
            LOGGER.info("RTS-Fix: Copilot chat not available, prompt copied to clipboard");
        }

        return opened;
    }

    /**
     * Schedules the automated sequence: switch to agent mode, paste, and send.
     */
    private static void scheduleAutomatedPromptEntry(String prompt) {
        CompletableFuture.runAsync(() -> {
            try {
                // Wait for Copilot chat to fully open
                TimeUnit.MILLISECONDS.sleep(COPILOT_OPEN_DELAY);

                Robot robot = new Robot();

                // Step 1: Switch to Agent mode using @ symbol and selecting agent
                switchToAgentMode(robot);

                // Step 2: Paste the prompt (Ctrl+V)
                TimeUnit.MILLISECONDS.sleep(PASTE_DELAY);
                pasteFromClipboard(robot);

                // Step 3: Send the message (Enter)
                TimeUnit.MILLISECONDS.sleep(SEND_DELAY);
                sendMessage(robot);

                LOGGER.info("RTS-Fix: Automated prompt entry completed successfully");

            } catch (AWTException e) {
                LOGGER.warn("RTS-Fix: Failed to create Robot for automation", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("RTS-Fix: Automation sequence interrupted", e);
            } catch (Exception e) {
                LOGGER.warn("RTS-Fix: Error during automated prompt entry", e);
            }
        });
    }

    /**
     * Switches Copilot to Agent mode by typing @agent and pressing Tab.
     */
    private static void switchToAgentMode(Robot robot) throws InterruptedException {
        // Type "@agent" to trigger agent mode selection
        // First, type @ (Shift + 2 on US keyboard)
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_2);
        robot.keyRelease(KeyEvent.VK_2);
        robot.keyRelease(KeyEvent.VK_SHIFT);

        TimeUnit.MILLISECONDS.sleep(50);

        // Type "agent"
        typeString(robot, "agent");

        TimeUnit.MILLISECONDS.sleep(AGENT_MODE_SWITCH_DELAY);

        // Press Tab or Enter to select the agent option
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);

        TimeUnit.MILLISECONDS.sleep(AGENT_MODE_SWITCH_DELAY);

        // Add a space after agent selection
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);

        TimeUnit.MILLISECONDS.sleep(100);
    }

    /**
     * Types a string character by character using Robot.
     */
    private static void typeString(Robot robot, String text) throws InterruptedException {
        for (char c : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            if (keyCode != KeyEvent.VK_UNDEFINED) {
                if (Character.isUpperCase(c)) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                } else {
                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);
                }
                TimeUnit.MILLISECONDS.sleep(30);
            }
        }
    }

    /**
     * Pastes content from clipboard using Ctrl+V.
     */
    private static void pasteFromClipboard(Robot robot) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    /**
     * Sends the message by pressing Enter.
     */
    private static void sendMessage(Robot robot) {
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
    }

    /**
     * Copies text to the system clipboard synchronously.
     */
    private static void copyToClipboardSync(@NotNull String text) {
        CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    /**
     * Attempts to open GitHub Copilot chat using various known action IDs.
     */
    private static boolean tryOpenCopilotChat(@NotNull Project project) {
        if (tryOpenCopilotToolWindow(project)) {
            return true;
        }
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
     */
    public static boolean isCopilotAvailable(@Nullable Project project) {
        if (project == null) {
            return false;
        }

        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
            if (toolWindowManager.getToolWindow(toolWindowId) != null) {
                return true;
            }
        }

        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : COPILOT_CHAT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                return true;
            }
        }

        return false;
    }
}

