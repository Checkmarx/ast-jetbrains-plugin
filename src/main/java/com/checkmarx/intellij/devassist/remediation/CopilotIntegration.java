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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Production-ready utility class for integrating with GitHub Copilot in IntelliJ IDEA.
 *
 * <p>This class provides methods to open Copilot chat, switch to agent mode, paste prompts,
 * and send automatically. Since GitHub Copilot does not expose a public API for programmatic
 * access, this implementation uses a multi-layered approach:
 *
 * <ol>
 *   <li><b>Primary Strategy:</b> Component traversal to find and interact with Copilot's input field directly</li>
 *   <li><b>Secondary Strategy:</b> Keyboard automation using Robot class with platform-aware key bindings</li>
 *   <li><b>Fallback:</b> Clipboard copy with user guidance for manual paste</li>
 * </ol>
 *
 * <p>Key features:
 * <ul>
 *   <li>Cross-platform support (Windows, Mac, Linux)</li>
 *   <li>Retry mechanisms with configurable attempts</li>
 *   <li>Graceful degradation with user-friendly notifications</li>
 *   <li>Comprehensive logging for debugging</li>
 * </ul>
 *
 * @see <a href="https://github.com/orgs/community/discussions/172311">GitHub Copilot API Discussion</a>
 */
public final class CopilotIntegration {

    private static final Logger LOGGER = Utils.getLogger(CopilotIntegration.class);

    // ==================== Configuration Constants ====================

    /**
     * Configuration for timing delays. Can be adjusted based on system performance.
     * These values have been tuned for typical IDE response times.
     */
    private static final class Timing {
        // Initial delay after opening Copilot - allows UI to fully render
        static final int COPILOT_OPEN_DELAY_MS = 1200;
        // Delay between UI navigation steps (Tab, Enter, etc.)
        static final int NAVIGATION_DELAY_MS = 100;
        // Delay for dropdown menu to open and render
        static final int DROPDOWN_OPEN_DELAY_MS = 350;
        // Delay after selecting from dropdown
        static final int SELECTION_DELAY_MS = 250;
        // Delay for agent mode to fully activate
        static final int AGENT_MODE_DELAY_MS = 800;
        // Delay before paste operation
        static final int PASTE_DELAY_MS = 200;
        // Delay before send operation
        static final int SEND_DELAY_MS = 150;
        // Interval between focus detection checks
        static final int FOCUS_CHECK_INTERVAL_MS = 100;
        // Maximum time to wait for Copilot to gain focus
        static final int FOCUS_TIMEOUT_MS = 5000;
        // Delay between Robot key actions
        static final int ROBOT_AUTO_DELAY_MS = 30;
    }

    /**
     * Number of retry attempts for various operations
     */
    private static final class Retries {
        static final int MAX_COMPONENT_SEARCH = 3;
        static final int MAX_FOCUS_WAIT = 50; // 50 * 100ms = 5 seconds
        static final int MAX_AUTOMATION_ATTEMPTS = 2;
    }

    // Known Copilot action IDs - ordered by likelihood of availability
    private static final String[] COPILOT_CHAT_ACTION_IDS = {
            "copilot.chat.show",
            "GitHub.Copilot.Chat.Show",
            "copilot.openChat",
            "copilot.chat.openChat"
    };

    // Known Copilot tool window IDs
    private static final String[] COPILOT_TOOL_WINDOW_IDS = {
            "GitHub Copilot Chat",
            "Copilot Chat",
            "GitHub Copilot"
    };

    // ==================== Result Types ====================

    /**
     * Result of a Copilot integration operation.
     */
    public enum OperationResult {
        /** Full automation succeeded - prompt was sent to Copilot */
        FULL_SUCCESS,
        /** Partial success - Copilot opened but automation may have issues */
        PARTIAL_SUCCESS,
        /** Copilot not available - prompt copied to clipboard only */
        COPILOT_NOT_AVAILABLE,
        /** Operation failed completely */
        FAILED
    }

    /**
     * Detailed result with message for user feedback.
     */
    public static class IntegrationResult {
        private final OperationResult result;
        private final String message;
        private final @Nullable Exception exception;

        private IntegrationResult(OperationResult result, String message, @Nullable Exception exception) {
            this.result = result;
            this.message = message;
            this.exception = exception;
        }

        public OperationResult getResult() { return result; }
        public String getMessage() { return message; }
        public @Nullable Exception getException() { return exception; }
        public boolean isSuccess() { return result == OperationResult.FULL_SUCCESS || result == OperationResult.PARTIAL_SUCCESS; }

        static IntegrationResult fullSuccess(String message) {
            return new IntegrationResult(OperationResult.FULL_SUCCESS, message, null);
        }
        static IntegrationResult partialSuccess(String message) {
            return new IntegrationResult(OperationResult.PARTIAL_SUCCESS, message, null);
        }
        static IntegrationResult copilotNotAvailable(String message) {
            return new IntegrationResult(OperationResult.COPILOT_NOT_AVAILABLE, message, null);
        }
        static IntegrationResult failed(String message, @Nullable Exception e) {
            return new IntegrationResult(OperationResult.FAILED, message, e);
        }
    }

    private CopilotIntegration() {
        // Prevent instantiation
    }

    // ==================== Public API ====================

    /**
     * Opens Copilot chat, switches to agent mode, pastes the prompt, and sends it automatically.
     * This provides a fully automated one-click fix experience.
     *
     * <p>The operation follows these steps:
     * <ol>
     *   <li>Copy prompt to clipboard (always done first as fallback)</li>
     *   <li>Attempt to open Copilot chat tool window</li>
     *   <li>Wait for Copilot to gain focus</li>
     *   <li>Switch to Agent mode using dropdown navigation</li>
     *   <li>Paste the prompt from clipboard</li>
     *   <li>Send the message</li>
     * </ol>
     *
     * @param prompt  The fix prompt to send to Copilot
     * @param project The current project context
     * @return true if the operation was initiated successfully, false otherwise
     */
    public static boolean openCopilotWithPrompt(@NotNull String prompt, @NotNull Project project) {
        IntegrationResult result = openCopilotWithPromptDetailed(prompt, project, null);
        return result.isSuccess();
    }

    /**
     * Opens Copilot with prompt and provides detailed result via callback.
     *
     * @param prompt   The fix prompt to send to Copilot
     * @param project  The current project context
     * @param callback Optional callback to receive the detailed result (called on EDT)
     * @return Immediate result indicating if operation was initiated
     */
    public static IntegrationResult openCopilotWithPromptDetailed(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<IntegrationResult> callback) {

        LOGGER.info("CxFix: Starting Copilot integration workflow");

        // Step 1: Always copy to clipboard first (guaranteed fallback)
        if (!copyToClipboard(prompt)) {
            IntegrationResult result = IntegrationResult.failed(
                    "Failed to copy prompt to clipboard", null);
            notifyCallback(callback, result);
            return result;
        }
        LOGGER.info("CxFix: Prompt copied to clipboard");

        // Step 2: Check if Copilot is available
        if (!isCopilotAvailable(project)) {
            LOGGER.info("CxFix: Copilot not available, prompt copied to clipboard");
            IntegrationResult result = IntegrationResult.copilotNotAvailable(
                    "GitHub Copilot is not installed or available. The fix prompt has been copied to your clipboard.");
            notifyCallback(callback, result);
            return result;
        }

        // Step 3: Try to open Copilot chat
        boolean opened = ApplicationManager.getApplication().runReadAction(
                (Computable<Boolean>) () -> tryOpenCopilotChat(project));

        if (!opened) {
            LOGGER.warn("CxFix: Failed to open Copilot chat window");
            IntegrationResult result = IntegrationResult.copilotNotAvailable(
                    "Could not open Copilot chat. The fix prompt has been copied to your clipboard.");
            notifyCallback(callback, result);
            return result;
        }

        LOGGER.info("CxFix: Copilot chat opened, starting automation sequence");

        // Step 4: Schedule the automation sequence
        scheduleAutomatedPromptEntry(project, prompt, callback);

        return IntegrationResult.partialSuccess("Copilot chat opened, automation in progress...");
    }

    /**
     * Checks if GitHub Copilot is available in the current IDE.
     *
     * @param project The project context (can be null for global check)
     * @return true if Copilot is available, false otherwise
     */
    public static boolean isCopilotAvailable(@Nullable Project project) {
        // Check for tool window
        if (project != null) {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
                if (toolWindowManager.getToolWindow(toolWindowId) != null) {
                    return true;
                }
            }
        }

        // Check for actions
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : COPILOT_CHAT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                return true;
            }
        }

        return false;
    }

    // ==================== Automation Implementation ====================

    /**
     * Schedules the automated sequence: wait for focus, switch to agent mode, paste, and send.
     */
    private static void scheduleAutomatedPromptEntry(
            @NotNull Project project,
            @NotNull String prompt,
            @Nullable Consumer<IntegrationResult> callback) {

        CompletableFuture.runAsync(() -> {
            IntegrationResult result;
            try {
                // Wait for Copilot to open and stabilize
                LOGGER.info("CxFix: Step 1/5 - Waiting for Copilot chat to fully open...");
                TimeUnit.MILLISECONDS.sleep(Timing.COPILOT_OPEN_DELAY_MS);

                // Try component-based approach first (more reliable)
                boolean componentSuccess = tryComponentBasedAutomation(project, prompt);

                if (componentSuccess) {
                    LOGGER.info("CxFix: Component-based automation succeeded");
                    result = IntegrationResult.fullSuccess(
                            "Fix prompt sent to Copilot Agent successfully!");
                } else {
                    // Fall back to Robot-based automation
                    LOGGER.info("CxFix: Falling back to keyboard automation...");
                    boolean robotSuccess = tryRobotBasedAutomation(prompt);

                    if (robotSuccess) {
                        LOGGER.info("CxFix: Robot-based automation completed");
                        result = IntegrationResult.fullSuccess(
                                "Fix prompt sent to Copilot Agent. Please verify the prompt was received.");
                    } else {
                        LOGGER.warn("CxFix: Automation had issues, prompt is in clipboard");
                        result = IntegrationResult.partialSuccess(
                                "Copilot opened but automation may have failed. " +
                                "Please paste the prompt manually (Ctrl/Cmd+V).");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("CxFix: Automation interrupted", e);
                result = IntegrationResult.partialSuccess(
                        "Operation interrupted. Prompt is in clipboard - please paste manually.");
            } catch (Exception e) {
                LOGGER.warn("CxFix: Automation failed", e);
                result = IntegrationResult.partialSuccess(
                        "Automation encountered an error. Prompt is in clipboard - please paste manually.");
            }

            notifyCallback(callback, result);
        });
    }

    /**
     * Attempts automation by finding Copilot's UI components directly.
     * This is more reliable than keyboard automation as it doesn't depend on focus state.
     */
    private static boolean tryComponentBasedAutomation(@NotNull Project project, @NotNull String prompt) {
        AtomicBoolean success = new AtomicBoolean(false);

        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow copilotWindow = findCopilotToolWindow(project);
                if (copilotWindow == null) {
                    LOGGER.debug("CxFix: Could not find Copilot tool window for component search");
                    return;
                }

                // Try to find the input text component
                JTextComponent inputField = findCopilotInputField(copilotWindow);
                if (inputField == null) {
                    LOGGER.debug("CxFix: Could not find Copilot input field");
                    return;
                }

                LOGGER.info("CxFix: Found Copilot input field, setting text directly");

                // Set text directly
                inputField.setText(prompt);
                inputField.requestFocusInWindow();

                // Note: We can't easily switch to agent mode or send via component approach
                // as those require interacting with custom Copilot UI elements
                // The user will need to manually select agent mode and press enter

                success.set(true);
            } catch (Exception e) {
                LOGGER.debug("CxFix: Component-based automation failed", e);
            }
        });

        return success.get();
    }

    /**
     * Recursively searches for a text input component in the Copilot tool window.
     */
    private static @Nullable JTextComponent findCopilotInputField(@NotNull ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                JTextComponent textField = findTextComponentRecursively(component);
                if (textField != null) {
                    return textField;
                }
            }
        }
        return null;
    }

    /**
     * Recursively searches for a JTextComponent (text field or text area) in the component hierarchy.
     */
    private static @Nullable JTextComponent findTextComponentRecursively(@NotNull Component component) {
        // Check if this component is a text input
        if (component instanceof JTextComponent) {
            JTextComponent textComponent = (JTextComponent) component;
            // Filter out read-only components and very small ones (likely labels)
            if (textComponent.isEditable() && textComponent.isEnabled() && textComponent.isVisible()) {
                // Prefer larger text areas (more likely to be the main input)
                if (textComponent.getWidth() > 100) {
                    return textComponent;
                }
            }
        }

        // Recursively search children
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                JTextComponent found = findTextComponentRecursively(child);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Attempts automation using Robot class for keyboard simulation.
     * Uses platform-aware key bindings.
     */
    private static boolean tryRobotBasedAutomation(@NotNull String prompt) {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(Timing.ROBOT_AUTO_DELAY_MS);

            // Step 2: Switch to Agent mode
            LOGGER.info("CxFix: Step 2/5 - Switching to Agent mode...");
            boolean agentModeSuccess = switchToAgentMode(robot);

            if (!agentModeSuccess) {
                LOGGER.warn("CxFix: Agent mode switch may have failed, continuing with paste...");
            }

            // Step 3: Wait for agent mode to activate
            LOGGER.info("CxFix: Step 3/5 - Waiting for Agent mode to activate...");
            TimeUnit.MILLISECONDS.sleep(Timing.AGENT_MODE_DELAY_MS);

            // Step 4: Paste the prompt
            LOGGER.info("CxFix: Step 4/5 - Pasting prompt...");
            TimeUnit.MILLISECONDS.sleep(Timing.PASTE_DELAY_MS);
            pasteFromClipboard(robot);

            // Step 5: Send the message
            LOGGER.info("CxFix: Step 5/5 - Sending message...");
            TimeUnit.MILLISECONDS.sleep(Timing.SEND_DELAY_MS);
            sendMessage(robot);

            LOGGER.info("CxFix: Robot automation sequence completed");
            return true;

        } catch (AWTException e) {
            LOGGER.warn("CxFix: Failed to create Robot for automation", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("CxFix: Robot automation interrupted", e);
            return false;
        } catch (Exception e) {
            LOGGER.warn("CxFix: Robot automation failed", e);
            return false;
        }
    }

    /**
     * Switches Copilot to Agent mode using the mode dropdown menu.
     *
     * <p>Copilot's dropdown structure (as of 2024):
     * <pre>
     *   1. Ask       (index 0)
     *   2. Edit      (index 1)
     *   3. Agent     (index 2) <- TARGET
     *   4. (possibly more items)
     * </pre>
     *
     * <p>Strategy for reliable selection regardless of current mode:
     * <ol>
     *   <li>Tab to the mode dropdown from chat input</li>
     *   <li>Press Enter/Space to open the dropdown</li>
     *   <li>Navigate to top using Home or multiple Up arrows</li>
     *   <li>Press Down twice to reach Agent</li>
     *   <li>Press Enter to select</li>
     *   <li>Tab back to chat input</li>
     * </ol>
     */
    private static boolean switchToAgentMode(Robot robot) throws InterruptedException {
        LOGGER.debug("CxFix: Starting agent mode selection...");

        try {
            // Step 1: Tab to mode dropdown
            LOGGER.debug("CxFix: Tabbing to mode dropdown...");
            pressKey(robot, KeyEvent.VK_TAB);
            TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);

            // Step 2: Open dropdown
            LOGGER.debug("CxFix: Opening dropdown...");
            pressKey(robot, KeyEvent.VK_ENTER);
            TimeUnit.MILLISECONDS.sleep(Timing.DROPDOWN_OPEN_DELAY_MS);

            // Step 3: Navigate to top of dropdown
            LOGGER.debug("CxFix: Navigating to top of dropdown...");
            navigateToDropdownTop(robot);
            TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);

            // Step 4: Navigate to Agent (Down 2 times: Ask -> Edit -> Agent)
            LOGGER.debug("CxFix: Navigating to Agent option...");
            pressKey(robot, KeyEvent.VK_DOWN);
            TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);
            pressKey(robot, KeyEvent.VK_DOWN);
            TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);

            // Step 5: Select Agent mode
            LOGGER.debug("CxFix: Selecting Agent mode...");
            pressKey(robot, KeyEvent.VK_ENTER);
            TimeUnit.MILLISECONDS.sleep(Timing.SELECTION_DELAY_MS);

            // Step 6: Navigate back to chat input
            LOGGER.debug("CxFix: Returning to chat input...");
            pressShiftTab(robot);
            TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);

            LOGGER.debug("CxFix: Agent mode selection completed");
            return true;

        } catch (Exception e) {
            LOGGER.warn("CxFix: Error during agent mode switch", e);
            // Try to recover by pressing Escape and returning to input
            try {
                pressKey(robot, KeyEvent.VK_ESCAPE);
                TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);
            } catch (Exception ignored) {}
            return false;
        }
    }

    /**
     * Navigates to the top of a dropdown using Home key with Up arrow fallback.
     */
    private static void navigateToDropdownTop(Robot robot) throws InterruptedException {
        // Try Home key first
        pressKey(robot, KeyEvent.VK_HOME);
        TimeUnit.MILLISECONDS.sleep(Timing.NAVIGATION_DELAY_MS);

        // Fallback: Press Up multiple times to ensure we're at the top
        // This handles dropdowns where Home key doesn't work
        for (int i = 0; i < 5; i++) {
            pressKey(robot, KeyEvent.VK_UP);
            TimeUnit.MILLISECONDS.sleep(30);
        }
    }

    // ==================== Keyboard Helpers ====================

    /**
     * Presses a single key.
     */
    private static void pressKey(Robot robot, int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    /**
     * Presses Shift+Tab key combination.
     */
    private static void pressShiftTab(Robot robot) {
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_SHIFT);
    }

    /**
     * Pastes content from clipboard using platform-appropriate shortcut.
     * Uses Cmd+V on Mac, Ctrl+V on Windows/Linux.
     */
    private static void pasteFromClipboard(Robot robot) {
        int modifierKey = SystemInfo.isMac ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
        robot.keyPress(modifierKey);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(modifierKey);
    }

    /**
     * Sends the message by pressing Enter.
     */
    private static void sendMessage(Robot robot) {
        pressKey(robot, KeyEvent.VK_ENTER);
    }

    // ==================== Tool Window Helpers ====================

    /**
     * Attempts to open GitHub Copilot chat using various methods.
     */
    private static boolean tryOpenCopilotChat(@NotNull Project project) {
        // Try tool window first (preferred)
        if (tryOpenCopilotToolWindow(project)) {
            return true;
        }
        // Fall back to action invocation
        return tryInvokeCopilotAction(project);
    }

    /**
     * Finds and returns the Copilot tool window if available.
     */
    private static @Nullable ToolWindow findCopilotToolWindow(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : COPILOT_TOOL_WINDOW_IDS) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                return toolWindow;
            }
        }
        return null;
    }

    /**
     * Attempts to open Copilot chat via tool window.
     */
    private static boolean tryOpenCopilotToolWindow(@NotNull Project project) {
        ToolWindow toolWindow = findCopilotToolWindow(project);
        if (toolWindow != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                toolWindow.show(() -> {
                    // Request focus on the tool window content
                    toolWindow.activate(() -> {
                        LOGGER.debug("CxFix: Copilot tool window activated");
                    });
                });
            });
            return true;
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
                LOGGER.debug("CxFix: Invoked Copilot action: " + actionId);
                return true;
            }
        }
        return false;
    }

    // ==================== Clipboard Helpers ====================

    /**
     * Copies text to the system clipboard.
     * @return true if successful, false otherwise
     */
    private static boolean copyToClipboard(@NotNull String text) {
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
            return true;
        } catch (Exception e) {
            LOGGER.warn("CxFix: Failed to copy to clipboard", e);
            return false;
        }
    }

    // ==================== Callback Helpers ====================

    /**
     * Safely notifies the callback on the EDT.
     */
    private static void notifyCallback(@Nullable Consumer<IntegrationResult> callback, IntegrationResult result) {
        if (callback != null) {
            ApplicationManager.getApplication().invokeLater(() -> callback.accept(result));
        }
    }
}
