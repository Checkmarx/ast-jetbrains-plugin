package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.common.utils.Utils;
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
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Utility class for integrating with GitHub Copilot Chat in IntelliJ IDEA.
 *
 * <p>
 * This class provides automated interaction with Copilot Chat to send fix
 * prompts.
 * Since GitHub Copilot does not expose a public API, this implementation uses
 * component-based UI automation with reflection to access Copilot's internal
 * components.
 *
 * <h3>Integration Flow:</h3>
 * <ol>
 * <li>Copy prompt to clipboard (safety fallback)</li>
 * <li>Open Copilot Chat tool window</li>
 * <li>Switch to Agent mode using popup simulation</li>
 * <li>Paste prompt into input field</li>
 * <li>Send message via Enter key simulation</li>
 * </ol>
 *
 * <h3>Agent Mode Selection:</h3>
 * <p>
 * The key insight is that Copilot's ChatModeComboBox requires the full popup
 * interaction sequence (open → select → close) to properly initialize Agent
 * mode.
 * Simply calling {@code setSelectedItem()} does not trigger the internal
 * handlers.
 *
 * <h3>Fallback Behavior:</h3>
 * <p>
 * If automation fails, the prompt remains in the clipboard and the user is
 * notified to paste manually.
 *
 * @see <a href="https://github.com/orgs/community/discussions/172311">GitHub
 *      Copilot API Discussion</a>
 */
public final class CopilotIntegration {

    private static final Logger LOGGER = Utils.getLogger(CopilotIntegration.class);

    // ==================== Configuration Constants ====================

    /**
     * Configuration for timing delays in UI automation.
     * These values are tuned for typical IDE response times.
     * 
     * <p>All delays can be overridden via system properties for troubleshooting:
     * <ul>
     *   <li>{@code -Dcx.copilot.delay.open=1500} - Delay after opening Copilot</li>
     *   <li>{@code -Dcx.copilot.delay.mode=1000} - Delay for Agent mode UI to load</li>
     *   <li>{@code -Dcx.copilot.delay.popup.open=150} - Delay for popup to open</li>
     *   <li>{@code -Dcx.copilot.delay.popup.select=150} - Delay after selecting in popup</li>
     *   <li>{@code -Dcx.copilot.delay.popup.close=250} - Delay after closing popup</li>
     * </ul>
     */
    private static final class Timing {
        /** Delay after opening Copilot to allow UI to fully render (default: 1200ms) */
        static final int COPILOT_OPEN_DELAY_MS = Integer.getInteger("cx.copilot.delay.open", 1200);

        /** Delay for Agent mode UI panel to fully load after mode switch (default: 800ms) */
        static final int AGENT_MODE_DELAY_MS = Integer.getInteger("cx.copilot.delay.mode", 800);

        /** Delay for dropdown popup to open (default: 100ms) */
        static final int POPUP_OPEN_DELAY_MS = Integer.getInteger("cx.copilot.delay.popup.open", 100);

        /** Delay after selecting item in dropdown (default: 100ms) */
        static final int POPUP_SELECT_DELAY_MS = Integer.getInteger("cx.copilot.delay.popup.select", 100);

        /** Delay after closing dropdown popup (default: 200ms) */
        static final int POPUP_CLOSE_DELAY_MS = Integer.getInteger("cx.copilot.delay.popup.close", 200);
    }

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

    // ==================== Copilot UI Component Constants ====================

    /** Copilot chat mode names */
    private static final class ChatMode {
        static final String AGENT = "agent";
        static final String ASK = "ask";
        static final String EDIT = "edit";
        static final String PLAN = "plan";
        /** Pattern to identify Agent mode by ID in combo box items */
        static final String AGENT_ID_PATTERN = "id=agent";
    }

    /** Copilot UI component class name patterns */
    private static final class CopilotUIComponents {
        static final String CHAT_MODE_COMBO_BOX = "ChatMode";
        static final String MODE_COMBO = "ModeCombo";
        static final String CHAT_MODE_ITEM = "ChatModeItem";
        static final String MODE = "Mode";
    }

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

        public OperationResult getResult() {
            return result;
        }

        public String getMessage() {
            return message;
        }

        public @Nullable Exception getException() {
            return exception;
        }

        public boolean isSuccess() {
            return result == OperationResult.FULL_SUCCESS || result == OperationResult.PARTIAL_SUCCESS;
        }

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
     * Opens Copilot chat, switches to agent mode, pastes the prompt, and sends it
     * automatically.
     * This provides a fully automated one-click fix experience.
     *
     * <p>
     * The operation follows these steps:
     * <ol>
     * <li>Copy prompt to clipboard (always done first as fallback)</li>
     * <li>Attempt to open Copilot chat tool window</li>
     * <li>Wait for Copilot to gain focus</li>
     * <li>Switch to Agent mode using dropdown navigation</li>
     * <li>Paste the prompt from clipboard</li>
     * <li>Send the message</li>
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
     * @param callback Optional callback to receive the detailed result (called on
     *                 EDT)
     * @return Immediate result indicating if operation was initiated
     */
    public static IntegrationResult openCopilotWithPromptDetailed(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<IntegrationResult> callback) {

        LOGGER.debug("CxFix: Starting Copilot integration workflow");

        // Step 1: Always copy to clipboard first (guaranteed fallback)
        if (!copyToClipboard(prompt)) {
            IntegrationResult result = IntegrationResult.failed(
                    "Failed to copy prompt to clipboard", null);
            notifyCallback(callback, result);
            return result;
        }
        LOGGER.debug("CxFix: Prompt copied to clipboard");

        // Step 2: Check if Copilot is available
        if (!isCopilotAvailable(project)) {
            LOGGER.debug("CxFix: Copilot not available, prompt copied to clipboard");
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

        LOGGER.debug("CxFix: Copilot chat opened, starting automation sequence");

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
     * Schedules the automated prompt entry sequence.
     * 
     * <p>
     * This method runs asynchronously to avoid blocking the EDT. It performs:
     * <ol>
     * <li>Wait for Copilot UI to fully initialize</li>
     * <li>Switch to Agent mode via component automation</li>
     * <li>Paste prompt and send message</li>
     * </ol>
     * 
     * <p>
     * If automation fails, the prompt remains in clipboard for manual paste.
     *
     * @param project  The current project context
     * @param prompt   The fix prompt to send
     * @param callback Optional callback for result notification
     */
    private static void scheduleAutomatedPromptEntry(
            @NotNull Project project,
            @NotNull String prompt,
            @Nullable Consumer<IntegrationResult> callback) {

        CompletableFuture.runAsync(() -> {
            IntegrationResult result;
            try {
                // Wait for Copilot to open and UI to stabilize
                LOGGER.debug("CxFix: Waiting for Copilot chat to initialize...");
                TimeUnit.MILLISECONDS.sleep(Timing.COPILOT_OPEN_DELAY_MS);

                // Attempt component-based automation (direct UI interaction)
                boolean success = tryComponentBasedAutomation(project, prompt);

                if (success) {
                    LOGGER.debug("CxFix: Automation completed successfully");
                    result = IntegrationResult.fullSuccess(
                            "Fix prompt sent to Copilot Agent successfully!");
                } else {
                    // Component automation failed - prompt is already in clipboard
                    LOGGER.warn("CxFix: Automation failed, prompt available in clipboard");
                    result = IntegrationResult.partialSuccess(
                            "Copilot opened but automation failed. " +
                                    "The fix prompt has been copied to your clipboard - please paste manually (Ctrl/Cmd+V).");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("CxFix: Automation interrupted", e);
                result = IntegrationResult.partialSuccess(
                        "Operation was interrupted. The fix prompt is in your clipboard - please paste manually.");
            } catch (Exception e) {
                LOGGER.warn("CxFix: Automation error: " + e.getMessage());
                result = IntegrationResult.partialSuccess(
                        "Automation encountered an error. The fix prompt is in your clipboard - please paste manually.");
            }

            notifyCallback(callback, result);
        });
    }

    /**
     * Performs component-based automation by directly interacting with Copilot's UI
     * components.
     *
     * <p>
     * This approach uses Swing component traversal and reflection to:
     * <ol>
     * <li>Find and interact with the ChatModeComboBox to switch to Agent mode</li>
     * <li>Wait for the Agent mode UI panel to load</li>
     * <li>Find the chat input field and set the prompt text</li>
     * <li>Send the message via button click or Enter key simulation</li>
     * </ol>
     *
     * @param project The current project context
     * @param prompt  The fix prompt to send
     * @return true if automation completed successfully, false otherwise
     */
    private static boolean tryComponentBasedAutomation(@NotNull Project project, @NotNull String prompt) {
        AtomicBoolean modeSwitchSuccess = new AtomicBoolean(false);

        // Phase 1: Switch to Agent mode (must run on EDT)
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow copilotWindow = findCopilotToolWindow(project);
                if (copilotWindow == null) {
                    LOGGER.warn("CxFix: Copilot tool window not found");
                    return;
                }

                // Debug: Log component hierarchy for troubleshooting
                logAllComponents(copilotWindow);

                // Switch to Agent mode using the ChatModeComboBox
                LOGGER.debug("CxFix: Switching to Agent mode...");
                boolean agentModeSet = trySetAgentModeFromDropdown(copilotWindow);
                if (agentModeSet) {
                    LOGGER.debug("CxFix: Agent mode activated successfully");
                    modeSwitchSuccess.set(true);
                } else {
                    LOGGER.warn("CxFix: Failed to switch to Agent mode");
                }
            } catch (Exception e) {
                LOGGER.warn("CxFix: Error during mode switch: " + e.getMessage());
            }
        });

        if (!modeSwitchSuccess.get()) {
            return false;
        }

        // Phase 2: Wait for Agent mode UI to fully initialize
        // When switching modes, Copilot recreates the chat panel asynchronously
        LOGGER.debug("CxFix: Waiting for Agent mode UI to initialize...");
        try {
            TimeUnit.MILLISECONDS.sleep(Timing.AGENT_MODE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Phase 3: Find input field and send prompt (must run on EDT)
        AtomicBoolean sendSuccess = new AtomicBoolean(false);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow copilotWindow = findCopilotToolWindow(project);
                if (copilotWindow == null) {
                    LOGGER.warn("CxFix: Copilot tool window not found after mode switch");
                    return;
                }

                // Find the input field in the newly created Agent mode panel
                LOGGER.debug("CxFix: Finding input field...");
                JTextComponent inputField = findCopilotInputField(copilotWindow);
                if (inputField == null) {
                    LOGGER.warn("CxFix: Could not find input field");
                    return;
                }

                // Set the prompt text and focus the field
                LOGGER.debug("CxFix: Setting prompt text...");
                inputField.setText(prompt);
                inputField.requestFocusInWindow();

                // Send the message
                LOGGER.debug("CxFix: Sending message...");
                boolean sent = trySendMessage(copilotWindow, inputField);
                if (sent) {
                    LOGGER.debug("CxFix: Message sent successfully");
                    sendSuccess.set(true);
                } else {
                    LOGGER.warn("CxFix: Failed to send message");
                }
            } catch (Exception e) {
                LOGGER.warn("CxFix: Error during text entry: " + e.getMessage());
            }
        });

        return sendSuccess.get();
    }

    /**
     * Sends the message using available methods in order of preference.
     *
     * <p>
     * Attempts:
     * <ol>
     * <li>Find and click a "Send" button</li>
     * <li>Find and click an action button near the input field</li>
     * <li>Simulate Enter key press on the input field</li>
     * </ol>
     *
     * @param toolWindow The Copilot tool window
     * @param inputField The chat input field
     * @return true if message was sent, false otherwise
     */
    private static boolean trySendMessage(@NotNull ToolWindow toolWindow, @NotNull JTextComponent inputField) {
        // Try to find and click a send button
        AbstractButton sendButton = findSendButton(toolWindow);
        if (sendButton != null && sendButton.isEnabled()) {
            LOGGER.debug("CxFix: Clicking send button");
            sendButton.doClick();
            return true;
        }

        // Try to find an action button (icon button without text) near the input
        AbstractButton actionButton = findActionButton(toolWindow, inputField);
        if (actionButton != null && actionButton.isEnabled()) {
            LOGGER.debug("CxFix: Clicking action button");
            actionButton.doClick();
            return true;
        }

        // Fall back to Enter key simulation
        LOGGER.debug("CxFix: Simulating Enter key");
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
                    LOGGER.debug("CxFix: Found send button by text: '" + text + "'");
                    return button;
                }
            }

            // Check tooltip
            if (tooltip != null) {
                String lowerTooltip = tooltip.toLowerCase();
                if (lowerTooltip.contains("send") || lowerTooltip.contains("submit") ||
                        lowerTooltip.contains("execute") || lowerTooltip.contains("run")) {
                    LOGGER.debug("CxFix: Found send button by tooltip: '" + tooltip + "'");
                    return button;
                }
            }

            // Check name
            if (name != null) {
                String lowerName = name.toLowerCase();
                if (lowerName.contains("send") || lowerName.contains("submit")) {
                    LOGGER.debug("CxFix: Found send button by name: '" + name + "'");
                    return button;
                }
            }

            // Check class name
            if (className.contains("send") || className.contains("submit")) {
                LOGGER.debug("CxFix: Found send button by class: " + button.getClass().getSimpleName());
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
    private static @Nullable AbstractButton findActionButton(@NotNull ToolWindow toolWindow,
            @NotNull JTextComponent inputField) {
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
                            LOGGER.debug("CxFix: Found action button near input field: " +
                                    button.getClass().getSimpleName());
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
                        LOGGER.debug(
                                "CxFix: Found potential send button by class: " + button.getClass().getSimpleName());
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
                    '\n');
            KeyEvent enterReleased = new KeyEvent(
                    inputField,
                    KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(),
                    0,
                    KeyEvent.VK_ENTER,
                    '\n');

            inputField.dispatchEvent(enterPressed);
            inputField.dispatchEvent(enterReleased);

            LOGGER.debug("CxFix: Enter key event dispatched");
            return true;
        } catch (Exception e) {
            LOGGER.warn("CxFix: Failed to simulate Enter key", e);
            return false;
        }
    }

    /**
     * Logs all UI components in the Copilot tool window for debugging purposes.
     * This is useful when Copilot's internal UI structure changes and automation
     * needs updating.
     */
    private static void logAllComponents(@NotNull ToolWindow toolWindow) {
        if (!LOGGER.isDebugEnabled()) {
            return; // Skip expensive component traversal if debug logging is disabled
        }

        LOGGER.debug("CxFix: === Starting component hierarchy dump ===");
        Content[] contents = toolWindow.getContentManager().getContents();
        for (int i = 0; i < contents.length; i++) {
            Content content = contents[i];
            JComponent component = content.getComponent();
            if (component != null) {
                LOGGER.debug("CxFix: Content[" + i + "] displayName: " + content.getDisplayName());
                logComponentHierarchy(component, 0);
            }
        }
        LOGGER.debug("CxFix: === End component hierarchy dump ===");
    }

    /**
     * Recursively logs component hierarchy with indentation for debugging.
     * Only logs when debug level is enabled to avoid performance impact.
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

        // Add extra info for specific component types of interest
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (i > 0)
                    items.append(", ");
                Object item = combo.getItemAt(i);
                String displayName = extractModeDisplayName(item);
                items.append("[").append(i).append("]=").append(displayName);
            }
            String selectedDisplay = extractModeDisplayName(combo.getSelectedItem());
            componentInfo += " ComboBox items: {" + items + "}, selected: " + selectedDisplay;
            LOGGER.debug("CxFix: FOUND COMBOBOX: " + componentInfo);
        } else if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            componentInfo += " Button text: '" + button.getText() + "'";
            componentInfo += ", tooltip: '" + button.getToolTipText() + "'";
            componentInfo += ", hasIcon: " + (button.getIcon() != null);
            componentInfo += ", enabled: " + button.isEnabled();
            LOGGER.debug("CxFix: Button: " + componentInfo);
        } else if (component instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) component;
            componentInfo += " Editable: " + text.isEditable() + ", Text length: " +
                    (text.getText() != null ? text.getText().length() : 0);
            LOGGER.debug("CxFix: TextComponent: " + componentInfo);
        }

        // Log components that might be mode selectors (useful for troubleshooting)
        String className = component.getClass().getName().toLowerCase();
        if (className.contains("dropdown") || className.contains("combo") ||
                className.contains("mode") || className.contains("picker")) {
            LOGGER.debug("CxFix: POTENTIAL MODE SELECTOR: " + componentInfo);
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
     * Attempts to switch to Agent mode by finding and interacting with the mode
     * dropdown.
     *
     * <p>
     * Searches the tool window contents for:
     * <ol>
     * <li>ChatModeComboBox (primary) - the standard mode dropdown</li>
     * <li>Mode button (fallback) - for alternative UI layouts</li>
     * </ol>
     *
     * @param toolWindow The Copilot tool window
     * @return true if Agent mode was successfully activated, false otherwise
     */
    private static boolean trySetAgentModeFromDropdown(@NotNull ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                // Primary: Find ChatModeComboBox
                JComboBox<?> comboBox = findChatModeComboBox(component);
                if (comboBox != null) {
                    return selectAgentInComboBox(comboBox);
                }

                // Fallback: Find mode button (for alternative UI layouts)
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
     *
     * <p>
     * Looks for the exact "Agent" mode (id=Agent) rather than other modes
     * that might have kind=Agent (like "Plan").
     *
     * @param comboBox The ChatModeComboBox
     * @return The index of Agent mode, or -1 if not found
     */
    private static int findAgentModeIndex(@NotNull JComboBox<?> comboBox) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            String displayName = extractModeDisplayName(item);
            // Look for exact "Agent" mode (id=Agent, not Plan which has kind=Agent)
            if (displayName.toLowerCase().contains(ChatMode.AGENT_ID_PATTERN)) {
                return i;
            }
        }
        // Fallback to index 2 if not found (typical position in Copilot's dropdown)
        if (comboBox.getItemCount() >= 3) {
            return 2;
        }
        return -1;
    }

    /**
     * Recursively searches for the ChatModeComboBox component.
     *
     * <p>
     * Identifies the combo box by:
     * <ol>
     * <li>Class name containing "ChatMode" or "ModeCombo"</li>
     * <li>Item types containing "ChatModeItem" or "Mode"</li>
     * </ol>
     *
     * @param component The root component to search from
     * @return The ChatModeComboBox if found, null otherwise
     */
    private static @Nullable JComboBox<?> findChatModeComboBox(@NotNull Component component) {
        if (component instanceof JComboBox) {
            JComboBox<?> combo = (JComboBox<?>) component;
            String className = combo.getClass().getSimpleName();

            // Check by class name
            if (className.contains(CopilotUIComponents.CHAT_MODE_COMBO_BOX) || className.contains(CopilotUIComponents.MODE_COMBO)) {
                LOGGER.debug("CxFix: Found ChatModeComboBox: " + className);
                logComboBoxItems(combo);
                return combo;
            }

            // Check by item type
            if (combo.getItemCount() > 0) {
                Object firstItem = combo.getItemAt(0);
                if (firstItem != null) {
                    String itemClassName = firstItem.getClass().getName();
                    if (itemClassName.contains(CopilotUIComponents.CHAT_MODE_ITEM) || itemClassName.contains(CopilotUIComponents.MODE)) {
                        LOGGER.debug("CxFix: Found mode ComboBox by item type: " + itemClassName);
                        logComboBoxItems(combo);
                        return combo;
                    }
                }
            }
        }

        // Recurse into children
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
     * Logs all items in a combo box for debugging purposes.
     */
    private static void logComboBoxItems(@NotNull JComboBox<?> comboBox) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug("CxFix: ComboBox has " + comboBox.getItemCount() + " items:");
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            String displayName = extractModeDisplayName(item);
            LOGGER.debug("CxFix:   [" + i + "] " + displayName + " (class: " +
                    (item != null ? item.getClass().getSimpleName() : "null") + ")");
        }
        Object selected = comboBox.getSelectedItem();
        LOGGER.debug("CxFix:   Currently selected: " + extractModeDisplayName(selected));
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
        String[] methodNames = { "getName", "getDisplayName", "getText", "getLabel", "getTitle", "name",
                "displayName" };
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
        String[] fieldNames = { "name", "displayName", "text", "label", "title", "mode", "value" };
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
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            // Log all declared fields
            for (Field f : item.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object result = f.get(item);
                    LOGGER.debug("CxFix:   Field " + f.getName() + " = " + result);
                } catch (Exception ignored) {
                }
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

        LOGGER.debug("CxFix: Agent mode found at index " + agentIndex);

        // Primary strategy: Simulate popup interaction
        // This properly initializes Copilot's Agent mode internal handlers
        if (selectAgentViaPopupSimulation(comboBox, agentItem)) {
            return true;
        }

        // Fallback: Direct selection (may not fully initialize Agent mode)
        LOGGER.warn("CxFix: Popup simulation failed, trying direct selection");
        comboBox.setSelectedIndex(agentIndex);
        return true;
    }

    /**
     * Selects Agent mode by simulating the full popup interaction sequence.
     *
     * <p>
     * This approach is necessary because Copilot's {@code ChatModeService} only
     * fully
     * initializes Agent mode when the complete popup lifecycle is executed:
     * <ol>
     * <li>Opening the popup prepares internal state in ChatModeService</li>
     * <li>Selecting while popup is visible triggers proper ItemListener
     * callbacks</li>
     * <li>Closing the popup completes the initialization and activates Agent
     * features</li>
     * </ol>
     *
     * <p>
     * Simply calling {@code setSelectedItem()} without the popup sequence does not
     * trigger the internal handlers, resulting in the UI showing "Agent" but the
     * backend still operating in "Ask" mode.
     *
     * <p>
     * <b>Note:</b> This method runs on the EDT and must not block. The popup
     * operations
     * are executed synchronously but quickly. The caller handles any necessary
     * waiting
     * via background threading.
     *
     * @param comboBox  The ChatModeComboBox component
     * @param agentItem The Agent mode item to select
     * @return true if Agent mode was successfully activated, false otherwise
     */
    @SuppressWarnings("unchecked")
    private static boolean selectAgentViaPopupSimulation(@NotNull JComboBox<?> comboBox, @NotNull Object agentItem) {
        try {
            LOGGER.debug("CxFix: Starting popup simulation for Agent mode");

            // Step 1: Open popup - prepares internal ChatModeService state
            comboBox.setPopupVisible(true);

            // Step 2: Select Agent item while popup is visible - triggers ItemListeners
            // Using invokeLater to let the popup fully render before selection
            ((JComboBox<Object>) comboBox).setSelectedItem(agentItem);

            // Step 3: Close popup - triggers final initialization
            comboBox.setPopupVisible(false);

            LOGGER.debug("CxFix: Popup simulation completed");
            return true;

        } catch (Exception e) {
            LOGGER.warn("CxFix: Agent mode switch error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Finds a button that represents the mode selector (fallback for non-combobox
     * UI).
     * Looks for buttons showing current mode text like "Ask", "Agent", etc.
     */
    private static @Nullable AbstractButton findModeButton(@NotNull Component component) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            String text = button.getText();
            if (text != null) {
                String lowerText = text.toLowerCase();
                if (lowerText.equals(ChatMode.ASK) || lowerText.equals(ChatMode.EDIT) ||
                        lowerText.equals(ChatMode.AGENT) || lowerText.equals(ChatMode.PLAN)) {
                    LOGGER.debug("CxFix: Found mode button: '" + text + "'");
                    return button;
                }
            }
            String tooltip = button.getToolTipText();
            if (tooltip != null && tooltip.toLowerCase().contains("mode")) {
                LOGGER.debug("CxFix: Found mode button by tooltip: '" + tooltip + "'");
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
     * Clicks a mode button to open dropdown and selects Agent (fallback method).
     * Used when ChatModeComboBox is not found but a mode button exists.
     */
    private static boolean clickAgentModeButton(@NotNull AbstractButton modeButton, @NotNull Component rootComponent) {
        String currentMode = modeButton.getText();
        LOGGER.debug("CxFix: Mode button text: '" + currentMode + "'");

        if (currentMode != null && currentMode.toLowerCase().contains(ChatMode.AGENT)) {
            LOGGER.debug("CxFix: Already in Agent mode");
            return true;
        }

        // Click the button to open the dropdown
        LOGGER.debug("CxFix: Opening mode dropdown via button click");
        modeButton.doClick();
        // Note: No sleep here - popup should be visible immediately after doClick
        // Any necessary delays are handled by the caller in background thread

        // Search for Agent option in popup windows
        Window[] windows = Window.getWindows();
        for (Window window : windows) {
            if (window.isVisible() && window instanceof JWindow) {
                AbstractButton agentButton = findButtonWithText(window, ChatMode.AGENT);
                if (agentButton != null) {
                    LOGGER.debug("CxFix: Selecting Agent from popup");
                    agentButton.doClick();
                    return true;
                }
                JMenuItem agentMenuItem = findMenuItemWithText(window, ChatMode.AGENT);
                if (agentMenuItem != null) {
                    LOGGER.debug("CxFix: Selecting Agent from menu");
                    agentMenuItem.doClick();
                    return true;
                }
            }
        }

        // Check popup menu via MenuSelectionManager
        MenuSelectionManager msm = MenuSelectionManager.defaultManager();
        MenuElement[] selectedPath = msm.getSelectedPath();
        if (selectedPath.length > 0) {
            for (MenuElement element : selectedPath) {
                if (element instanceof JPopupMenu) {
                    JPopupMenu popup = (JPopupMenu) element;
                    for (Component menuComp : popup.getComponents()) {
                        if (menuComp instanceof JMenuItem) {
                            JMenuItem item = (JMenuItem) menuComp;
                            if (item.getText() != null && item.getText().toLowerCase().contains(ChatMode.AGENT)) {
                                LOGGER.debug("CxFix: Selecting Agent from popup menu");
                                item.doClick();
                                return true;
                            }
                        }
                    }
                }
            }
        }

        LOGGER.warn("CxFix: Could not find Agent option in mode dropdown");
        return false;
    }

    /**
     * Finds a button with specific text (case-insensitive) in component hierarchy.
     */
    private static @Nullable AbstractButton findButtonWithText(@NotNull Component component,
            @NotNull String textToFind) {
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
     * Finds a menu item with specific text (case-insensitive) in component
     * hierarchy.
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
     * Recursively searches for a JTextComponent (text field or text area) in the
     * component hierarchy.
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
     * Must run on EDT to avoid "System clipboard is unavailable" issues.
     * 
     * @return true if successful, false otherwise
     */
    private static boolean copyToClipboard(@NotNull String text) {
        try {
            // Clipboard access must happen on EDT
            if (ApplicationManager.getApplication().isDispatchThread()) {
                CopyPasteManager.getInstance().setContents(new StringSelection(text));
            } else {
                ApplicationManager.getApplication()
                        .invokeAndWait(() -> CopyPasteManager.getInstance().setContents(new StringSelection(text)));
            }
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
