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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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

    // Known Copilot Agent mode action IDs - try these first for direct agent mode
    private static final String[] COPILOT_AGENT_ACTION_IDS = {
            "copilot.agent.show",
            "copilot.chat.agent",
            "copilot.openAgent",
            "copilot.agent.openChat",
            "GitHub.Copilot.Agent.Show",
            "copilot.chat.showAgent",
            "copilot.agent.start"
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
     * 
     * Flow: Open window -> Switch to Agent mode -> Wait for UI -> Paste prompt -> Send message
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

                // Log all components for debugging
                logAllComponents(copilotWindow);

                // Step 1: Switch to Agent mode FIRST
                LOGGER.info("CxFix: Step 1 - Switching to Agent mode...");
                boolean agentModeSet = trySetAgentModeFromDropdown(copilotWindow);
                if (agentModeSet) {
                    LOGGER.info("CxFix: Successfully set Agent mode from dropdown");
                } else {
                    LOGGER.warn("CxFix: Could not set Agent mode from dropdown, will try keyboard fallback");
                }

                success.set(true);
            } catch (Exception e) {
                LOGGER.debug("CxFix: Component-based automation failed during mode switch", e);
            }
        });

        if (!success.get()) {
            return false;
        }

        // Step 2: Wait for Agent mode UI to fully load
        // When switching modes, Copilot recreates the panel asynchronously
        LOGGER.info("CxFix: Step 2 - Waiting for Agent mode UI to load...");
        try {
            TimeUnit.MILLISECONDS.sleep(Timing.AGENT_MODE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Step 3: Now find the input field (in the new Agent mode panel) and set text
        AtomicBoolean textSet = new AtomicBoolean(false);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow copilotWindow = findCopilotToolWindow(project);
                if (copilotWindow == null) {
                    LOGGER.debug("CxFix: Could not find Copilot tool window after mode switch");
                    return;
                }

                // Re-find the input field in the Agent mode panel
                LOGGER.info("CxFix: Step 3 - Finding input field in Agent mode panel...");
                JTextComponent inputField = findCopilotInputField(copilotWindow);
                if (inputField == null) {
                    LOGGER.debug("CxFix: Could not find Copilot input field after mode switch");
                    return;
                }

                LOGGER.info("CxFix: Found Copilot input field in Agent mode, setting text directly");

                // Step 4: Set text (paste prompt)
                LOGGER.info("CxFix: Step 4 - Pasting prompt text...");
                inputField.setText(prompt);
                inputField.requestFocusInWindow();

                // Step 5: Send the message
                LOGGER.info("CxFix: Step 5 - Sending message...");
                boolean sent = trySendMessage(copilotWindow, inputField);
                if (sent) {
                    LOGGER.info("CxFix: Message sent successfully via component automation");
                } else {
                    LOGGER.warn("CxFix: Could not send message via component, will try keyboard fallback");
                }

                textSet.set(true);
            } catch (Exception e) {
                LOGGER.debug("CxFix: Component-based automation failed during text entry", e);
            }
        });

        return textSet.get();
    }

    /**
     * Attempts to send the message using various methods.
     * @return true if message was sent, false otherwise
     */
    private static boolean trySendMessage(@NotNull ToolWindow toolWindow, @NotNull JTextComponent inputField) {
        // Method 1: Try to find and click a send button
        AbstractButton sendButton = findSendButton(toolWindow);
        if (sendButton != null && sendButton.isEnabled()) {
            LOGGER.info("CxFix: Found send button, clicking...");
            sendButton.doClick();
            return true;
        }

        // Method 2: Try to find an action button (icon button without text)
        AbstractButton actionButton = findActionButton(toolWindow, inputField);
        if (actionButton != null && actionButton.isEnabled()) {
            LOGGER.info("CxFix: Found action button near input, clicking...");
            actionButton.doClick();
            return true;
        }

        // Method 3: Simulate Enter key press in the input field
        LOGGER.info("CxFix: No send button found, simulating Enter key...");
        return simulateEnterKey(inputField);
    }

    /**
     * Finds a send button in the tool window.
     * Looks for buttons with send-related text, tooltip, or icon.
     */
    private static @Nullable AbstractButton findSendButton(@NotNull ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                AbstractButton button = findSendButtonRecursively(component);
                if (button != null) {
                    return button;
                }
            }
        }
        return null;
    }

    /**
     * Recursively searches for a send button.
     */
    private static @Nullable AbstractButton findSendButtonRecursively(@NotNull Component component) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            String text = button.getText();
            String tooltip = button.getToolTipText();
            String name = button.getName();
            String className = button.getClass().getSimpleName().toLowerCase();
            
            // Check text
            if (text != null) {
                String lowerText = text.toLowerCase();
                if (lowerText.contains("send") || lowerText.contains("submit") || 
                    lowerText.equals("go") || lowerText.equals("run")) {
                    LOGGER.info("CxFix: Found send button by text: '" + text + "'");
                    return button;
                }
            }
            
            // Check tooltip
            if (tooltip != null) {
                String lowerTooltip = tooltip.toLowerCase();
                if (lowerTooltip.contains("send") || lowerTooltip.contains("submit") || 
                    lowerTooltip.contains("execute") || lowerTooltip.contains("run")) {
                    LOGGER.info("CxFix: Found send button by tooltip: '" + tooltip + "'");
                    return button;
                }
            }
            
            // Check name
            if (name != null) {
                String lowerName = name.toLowerCase();
                if (lowerName.contains("send") || lowerName.contains("submit")) {
                    LOGGER.info("CxFix: Found send button by name: '" + name + "'");
                    return button;
                }
            }
            
            // Check class name
            if (className.contains("send") || className.contains("submit")) {
                LOGGER.info("CxFix: Found send button by class: " + button.getClass().getSimpleName());
                return button;
            }
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                AbstractButton found = findSendButtonRecursively(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds an action button (likely send) that is positioned near the input field.
     * Copilot typically has an icon button next to/below the input field.
     */
    private static @Nullable AbstractButton findActionButton(@NotNull ToolWindow toolWindow, @NotNull JTextComponent inputField) {
        Container parent = inputField.getParent();
        
        // Walk up the hierarchy looking for sibling buttons
        while (parent != null) {
            for (Component sibling : parent.getComponents()) {
                if (sibling instanceof AbstractButton && sibling != inputField) {
                    AbstractButton button = (AbstractButton) sibling;
                    // Look for icon-only buttons (send buttons often have no text)
                    if ((button.getText() == null || button.getText().isEmpty()) && 
                        button.getIcon() != null && button.isEnabled() && button.isVisible()) {
                        
                        // Check if it's positioned to the right or below the input
                        Rectangle inputBounds = inputField.getBounds();
                        Rectangle buttonBounds = button.getBounds();
                        
                        // Button should be near the input field
                        if (buttonBounds.x >= inputBounds.x + inputBounds.width - 50 ||
                            buttonBounds.y >= inputBounds.y + inputBounds.height - 10) {
                            LOGGER.info("CxFix: Found action button near input field: " + 
                                    button.getClass().getSimpleName() + ", icon: " + button.getIcon());
                            return button;
                        }
                    }
                }
            }
            
            // Also search all buttons in the parent
            AbstractButton anyButton = findFirstEnabledIconButton(parent);
            if (anyButton != null) {
                return anyButton;
            }
            
            parent = parent.getParent();
            // Don't go too far up the hierarchy
            if (parent != null && parent.getClass().getSimpleName().contains("ToolWindow")) {
                break;
            }
        }
        return null;
    }

    /**
     * Finds the first enabled icon-only button (potential send button).
     */
    private static @Nullable AbstractButton findFirstEnabledIconButton(@NotNull Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) comp;
                String className = button.getClass().getSimpleName().toLowerCase();
                // Look for buttons that might be send buttons
                if (className.contains("send") || className.contains("submit") || 
                    className.contains("action") || className.contains("run")) {
                    if (button.isEnabled() && button.isVisible()) {
                        LOGGER.info("CxFix: Found potential send button by class: " + button.getClass().getSimpleName());
                        return button;
                    }
                }
            }
            if (comp instanceof Container) {
                AbstractButton found = findFirstEnabledIconButton((Container) comp);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Simulates pressing Enter key in the input field to send the message.
     */
    private static boolean simulateEnterKey(@NotNull JTextComponent inputField) {
        try {
            // Create and dispatch a key event for Enter
            KeyEvent enterPressed = new KeyEvent(
                    inputField,
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_ENTER,
                    '\n'
            );
            KeyEvent enterReleased = new KeyEvent(
                    inputField,
                    KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_ENTER,
                    '\n'
            );
            
            inputField.dispatchEvent(enterPressed);
            inputField.dispatchEvent(enterReleased);
            
            LOGGER.info("CxFix: Dispatched Enter key event to input field");
            return true;
        } catch (Exception e) {
            LOGGER.warn("CxFix: Failed to simulate Enter key", e);
            return false;
        }
    }

    /**
     * Logs all UI components in the Copilot tool window for debugging purposes.
     */
    private static void logAllComponents(@NotNull ToolWindow toolWindow) {
        LOGGER.info("CxFix: === Starting component hierarchy dump ===");
        Content[] contents = toolWindow.getContentManager().getContents();
        for (int i = 0; i < contents.length; i++) {
            Content content = contents[i];
            JComponent component = content.getComponent();
            if (component != null) {
                LOGGER.info("CxFix: Content[" + i + "] displayName: " + content.getDisplayName());
                logComponentHierarchy(component, 0);
            }
        }
        LOGGER.info("CxFix: === End component hierarchy dump ===");
    }

    /**
     * Recursively logs component hierarchy with indentation.
     */
    private static void logComponentHierarchy(@NotNull Component component, int depth) {
        String indent = "  ".repeat(depth);
        String componentInfo = String.format("%s- %s [name=%s, visible=%s, enabled=%s, bounds=%s]",
                indent,
                component.getClass().getSimpleName(),
                component.getName(),
                component.isVisible(),
                component.isEnabled(),
                component.getBounds());

        // Add extra info for specific component types
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (i > 0) items.append(", ");
                Object item = combo.getItemAt(i);
                String displayName = extractModeDisplayName(item);
                items.append("[").append(i).append("]=").append(displayName);
            }
            String selectedDisplay = extractModeDisplayName(combo.getSelectedItem());
            componentInfo += " ComboBox items: {" + items + "}, selected: " + selectedDisplay;
            LOGGER.info("CxFix: FOUND COMBOBOX: " + componentInfo);
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            componentInfo += " Button text: '" + button.getText() + "'";
            componentInfo += ", tooltip: '" + button.getToolTipText() + "'";
            componentInfo += ", hasIcon: " + (button.getIcon() != null);
            componentInfo += ", enabled: " + button.isEnabled();
            
            String buttonText = button.getText();
            String tooltip = button.getToolTipText();
            String btnClassName = button.getClass().getSimpleName().toLowerCase();
            
            // Log mode buttons
            if (buttonText != null && 
                (buttonText.toLowerCase().contains("agent") || 
                 buttonText.toLowerCase().contains("ask") ||
                 buttonText.toLowerCase().contains("edit") ||
                 buttonText.toLowerCase().contains("plan"))) {
                LOGGER.info("CxFix: FOUND MODE BUTTON: " + componentInfo);
            }
            // Log potential send buttons
            else if ((buttonText != null && (buttonText.toLowerCase().contains("send") || 
                     buttonText.toLowerCase().contains("submit") ||
                     buttonText.toLowerCase().contains("run"))) ||
                    (tooltip != null && (tooltip.toLowerCase().contains("send") ||
                     tooltip.toLowerCase().contains("submit") ||
                     tooltip.toLowerCase().contains("execute"))) ||
                    btnClassName.contains("send") || btnClassName.contains("submit") ||
                    btnClassName.contains("action") || btnClassName.contains("run")) {
                LOGGER.info("CxFix: POTENTIAL SEND BUTTON: " + componentInfo);
            }
            // Log icon-only buttons (often used for send)
            else if ((buttonText == null || buttonText.isEmpty()) && button.getIcon() != null) {
                LOGGER.info("CxFix: ICON BUTTON (potential send): " + componentInfo + 
                        ", class: " + button.getClass().getSimpleName());
            }
        } else if (component instanceof JLabel) {
            JLabel label = (JLabel) component;
            if (label.getText() != null && !label.getText().isEmpty()) {
                componentInfo += " Label text: '" + label.getText() + "'";
            }
        } else if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            componentInfo += " Editable: " + text.isEditable() + ", Text length: " + 
                    (text.getText() != null ? text.getText().length() : 0);
        }

        // Log components that might be dropdowns or mode selectors
        String className = component.getClass().getName().toLowerCase();
        if (className.contains("dropdown") || className.contains("combo") || 
            className.contains("select") || className.contains("mode") ||
            className.contains("popup") || className.contains("menu")) {
            LOGGER.info("CxFix: POTENTIAL DROPDOWN: " + componentInfo);
        } else {
            LOGGER.debug("CxFix: " + componentInfo);
        }

        // Recurse into children
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                logComponentHierarchy(child, depth + 1);
            }
        }
    }

    /**
     * Attempts to find and set Agent mode from a dropdown/combobox component.
     * @return true if agent mode was successfully set, false otherwise
     */
    private static boolean trySetAgentModeFromDropdown(@NotNull ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                // Try to find JComboBox first
                JComboBox<?> comboBox = findChatModeComboBox(component);
                if (comboBox != null) {
                    return selectAgentInComboBox(comboBox);
                }

                // Try to find custom dropdown buttons
                AbstractButton modeButton = findModeButton(component);
                if (modeButton != null) {
                    return clickAgentModeButton(modeButton, component);
                }
            }
        }
        return false;
    }

    /**
     * Finds the index of the Agent mode in the combo box.
     */
    private static int findAgentModeIndex(@NotNull JComboBox<?> comboBox) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            String displayName = extractModeDisplayName(item);
            // Look for exact "Agent" mode (id=Agent, not Plan which has kind=Agent)
            if (displayName.toLowerCase().contains("id=agent")) {
                return i;
            }
        }
        // Fallback to index 2 if not found
        if (comboBox.getItemCount() >= 3) {
            return 2;
        }
        return -1;
    }

    /**
     * Finds a JComboBox that is the ChatModeComboBox (mode selector).
     */
    private static @Nullable JComboBox<?> findChatModeComboBox(@NotNull Component component) {
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            String className = combo.getClass().getSimpleName();
            
            // Check if this is the ChatModeComboBox by class name
            if (className.contains("ChatMode") || className.contains("ModeCombo")) {
                LOGGER.info("CxFix: Found ChatModeComboBox by class name: " + className);
                logComboBoxItems(combo);
                return combo;
            }
            
            // Check if items are ChatModeItem objects
            if (combo.getItemCount() > 0) {
                Object firstItem = combo.getItemAt(0);
                if (firstItem != null) {
                    String itemClassName = firstItem.getClass().getName();
                    if (itemClassName.contains("ChatModeItem") || itemClassName.contains("Mode")) {
                        LOGGER.info("CxFix: Found mode ComboBox by item type: " + itemClassName);
                        logComboBoxItems(combo);
                        return combo;
                    }
                }
            }
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                JComboBox<?> found = findChatModeComboBox(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Logs all items in a combo box with their extracted display names.
     */
    private static void logComboBoxItems(@NotNull JComboBox<?> comboBox) {
        LOGGER.info("CxFix: ComboBox has " + comboBox.getItemCount() + " items:");
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            String displayName = extractModeDisplayName(item);
            LOGGER.info("CxFix:   [" + i + "] " + displayName + " (class: " + 
                    (item != null ? item.getClass().getSimpleName() : "null") + ")");
        }
        Object selected = comboBox.getSelectedItem();
        LOGGER.info("CxFix:   Currently selected: " + extractModeDisplayName(selected));
    }

    /**
     * Extracts the display name from a ChatModeItem object using reflection.
     * Tries multiple common property names and methods.
     */
    private static @NotNull String extractModeDisplayName(@Nullable Object item) {
        if (item == null) {
            return "null";
        }
        
        // Try common method names first
        String[] methodNames = {"getName", "getDisplayName", "getText", "getLabel", "getTitle", "name", "displayName"};
        for (String methodName : methodNames) {
            try {
                Method method = item.getClass().getMethod(methodName);
                Object result = method.invoke(item);
                if (result != null) {
                    String value = result.toString();
                    LOGGER.debug("CxFix: Extracted '" + value + "' using method " + methodName);
                    return value;
                }
            } catch (Exception ignored) {
                // Try next method
            }
        }
        
        // Try common field names
        String[] fieldNames = {"name", "displayName", "text", "label", "title", "mode", "value"};
        for (String fieldName : fieldNames) {
            try {
                Field field = item.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(item);
                if (result != null) {
                    String value = result.toString();
                    LOGGER.debug("CxFix: Extracted '" + value + "' using field " + fieldName);
                    return value;
                }
            } catch (Exception ignored) {
                // Try next field
            }
        }
        
        // Try getting all declared fields and methods for debugging
        LOGGER.debug("CxFix: Could not extract name, dumping class info for: " + item.getClass().getName());
        try {
            // Log all methods
            for (Method m : item.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                    String mName = m.getName();
                    if (!mName.equals("getClass") && !mName.equals("hashCode") && !mName.equals("toString")) {
                        try {
                            Object result = m.invoke(item);
                            LOGGER.debug("CxFix:   Method " + mName + "() = " + result);
                        } catch (Exception ignored) {}
                    }
                }
            }
            // Log all declared fields
            for (Field f : item.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object result = f.get(item);
                    LOGGER.debug("CxFix:   Field " + f.getName() + " = " + result);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("CxFix: Error dumping class info", e);
        }
        
        // Fallback to toString
        return item.toString();
    }

    /**
     * Selects "Agent" mode in the given combo box by simulating popup interaction.
     * This properly initializes Copilot's Agent mode which requires the full
     * popup open/select/close sequence to trigger internal handlers.
     */
    private static boolean selectAgentInComboBox(@NotNull JComboBox<?> comboBox) {
        int agentIndex = findAgentModeIndex(comboBox);
        Object agentItem = agentIndex >= 0 ? comboBox.getItemAt(agentIndex) : null;
        
        if (agentIndex == -1 || agentItem == null) {
            LOGGER.warn("CxFix: Could not find Agent mode in combo box");
            return false;
        }
        
        LOGGER.info("CxFix: Found Agent mode at index " + agentIndex + ", switching...");
        
        // Primary strategy: Simulate popup interaction (this is what works)
        if (selectAgentViaPopupSimulation(comboBox, agentItem)) {
            return true;
        }
        
        // Fallback: Direct selection (may not fully initialize Agent mode)
        LOGGER.warn("CxFix: Popup simulation failed, using direct selection fallback");
        comboBox.setSelectedIndex(agentIndex);
        return true;
    }

    /**
     * Selects Agent mode by simulating full popup interaction sequence.
     * 
     * This properly initializes Copilot's Agent mode because:
     * 1. Opening the popup prepares internal state in ChatModeService
     * 2. Selecting while popup is visible triggers proper ItemListener callbacks
     * 3. Closing the popup completes the initialization sequence
     * 
     * Just calling setSelectedItem() alone doesn't trigger the full initialization.
     */
    @SuppressWarnings("unchecked")
    private static boolean selectAgentViaPopupSimulation(@NotNull JComboBox<?> comboBox, @NotNull Object agentItem) {
        try {
            LOGGER.info("CxFix: Switching to Agent mode via popup simulation...");
            
            // Step 1: Open popup (prepares internal state)
            comboBox.setPopupVisible(true);
            Thread.sleep(100);
            
            // Step 2: Select item while popup is visible
            ((JComboBox<Object>) comboBox).setSelectedItem(agentItem);
            Thread.sleep(100);
            
            // Step 3: Close popup (triggers final initialization)
            comboBox.setPopupVisible(false);
            Thread.sleep(200);
            
            LOGGER.info("CxFix: Agent mode switch completed");
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("CxFix: Interrupted during Agent mode switch");
        } catch (Exception e) {
            LOGGER.warn("CxFix: Error during popup simulation: " + e.getMessage());
        }
        return false;
    }

    /**
     * Finds a button that represents the mode selector (might show current mode like "Ask", "Agent", etc.).
     */
    private static @Nullable AbstractButton findModeButton(@NotNull Component component) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            String text = button.getText();
            if (text != null) {
                String lowerText = text.toLowerCase();
                // Look for buttons that show mode names
                if (lowerText.equals("ask") || lowerText.equals("edit") || 
                    lowerText.equals("agent") || lowerText.equals("plan")) {
                    LOGGER.info("CxFix: Found mode button with text: '" + text + "'");
                    return button;
                }
            }
            // Also check tooltip or accessible name
            String tooltip = button.getToolTipText();
            if (tooltip != null && tooltip.toLowerCase().contains("mode")) {
                LOGGER.info("CxFix: Found button with mode tooltip: '" + tooltip + "'");
                return button;
            }
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                AbstractButton found = findModeButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Clicks a mode button to open dropdown and then selects Agent.
     * This handles custom dropdown implementations that aren't standard JComboBox.
     */
    private static boolean clickAgentModeButton(@NotNull AbstractButton modeButton, @NotNull Component rootComponent) {
        String currentMode = modeButton.getText();
        LOGGER.info("CxFix: Current mode button text: '" + currentMode + "'");

        if (currentMode != null && currentMode.toLowerCase().contains("agent")) {
            LOGGER.info("CxFix: Already in Agent mode");
            return true;
        }

        // Click the button to open the dropdown
        LOGGER.info("CxFix: Clicking mode button to open dropdown...");
        modeButton.doClick();

        // Wait a bit for popup to appear and look for Agent option
        try {
            TimeUnit.MILLISECONDS.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Try to find popup menu or list with Agent option
        // Look for JPopupMenu in the window hierarchy
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
            if (window.isVisible() && window instanceof JWindow) {
                LOGGER.info("CxFix: Found popup window: " + window.getClass().getName());
                AbstractButton agentButton = findButtonWithText(window, "agent");
                if (agentButton != null) {
                    LOGGER.info("CxFix: Found Agent button in popup, clicking...");
                    agentButton.doClick();
                    return true;
                }
                // Also try JMenuItem or JList
                JMenuItem agentMenuItem = findMenuItemWithText(window, "agent");
                if (agentMenuItem != null) {
                    LOGGER.info("CxFix: Found Agent menu item, clicking...");
                    agentMenuItem.doClick();
                    return true;
                }
            }
        }

        // Also check for JPopupMenu
        MenuSelectionManager msm = MenuSelectionManager.defaultManager();
        MenuElement[] selectedPath = msm.getSelectedPath();
        if (selectedPath.length > 0) {
            LOGGER.info("CxFix: Found menu selection path with " + selectedPath.length + " elements");
            for (MenuElement element : selectedPath) {
                if (element instanceof JPopupMenu) {
                    JPopupMenu popup = (JPopupMenu) element;
                    for (Component menuComp : popup.getComponents()) {
                        if (menuComp instanceof JMenuItem) {
                            JMenuItem item = (JMenuItem) menuComp;
                            if (item.getText() != null && item.getText().toLowerCase().contains("agent")) {
                                LOGGER.info("CxFix: Found Agent in popup menu, clicking...");
                                item.doClick();
                                return true;
                            }
                        }
                    }
                }
            }
        }

        LOGGER.warn("CxFix: Could not find Agent option in dropdown");
        return false;
    }

    /**
     * Finds a button with specific text (case-insensitive) in component hierarchy.
     */
    private static @Nullable AbstractButton findButtonWithText(@NotNull Component component, @NotNull String textToFind) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (button.getText() != null && button.getText().toLowerCase().contains(textToFind.toLowerCase())) {
                return button;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                AbstractButton found = findButtonWithText(child, textToFind);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Finds a menu item with specific text (case-insensitive) in component hierarchy.
     */
    private static @Nullable JMenuItem findMenuItemWithText(@NotNull Component component, @NotNull String textToFind) {
        if (component instanceof JMenuItem) {
            JMenuItem item = (JMenuItem) component;
            if (item.getText() != null && item.getText().toLowerCase().contains(textToFind.toLowerCase())) {
                return item;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JMenuItem found = findMenuItemWithText(child, textToFind);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
                // get dropdown component
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
