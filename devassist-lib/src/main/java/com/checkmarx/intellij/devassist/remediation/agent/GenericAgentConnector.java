package com.checkmarx.intellij.devassist.remediation.agent;

import com.intellij.openapi.actionSystem.*;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single, fully configuration-driven AI agent connector.
 * <p>
 * ALL behavior is parameterized by {@link AgentDefinition} loaded from {@code agents-config.json}.
 * There is NO agent-specific Java code. Adding a new AI agent requires only a new JSON entry.
 * <p>
 * Automation pipeline (per agent):
 * <ol>
 *   <li>Copy prompt to clipboard (universal safety fallback)</li>
 *   <li>Open the agent's chat window via configured tool window IDs or action IDs</li>
 *   <li>Switch to agent/agentic mode via configured strategy (combo box popup, button click, or none)</li>
 *   <li>Find the text input field via recursive Swing component traversal</li>
 *   <li>Set prompt text and send via configured strategy (button click or Enter key)</li>
 * </ol>
 */
public final class GenericAgentConnector {

    private static final Logger LOG = Logger.getInstance(GenericAgentConnector.class);

    private GenericAgentConnector() {
        // Static utility class
    }

    // ==================== Public API ====================

    /**
     * Sends the prompt to the highest-priority available AI agent.
     * <p>
     * Iterates agents in priority order (from config), tries each available agent.
     * If an agent is available but delivery fails, falls through to the next agent.
     *
     * @param prompt  the remediation or explanation prompt
     * @param project the current IntelliJ project
     * @return true if the prompt was delivered to any agent, false if no agent is available
     */
    public static boolean sendPrompt(@NotNull String prompt, @NotNull Project project) {
        List<AgentDefinition> agents = AgentConfigLoader.getInstance().getAgents();

        for (AgentDefinition agent : agents) {
            if (isAvailable(agent, project)) {
                LOG.info("CxFix-Generic: Agent selected: " + agent.getDisplayName() + " (id=" + agent.getId() + ")");
                try {
                    boolean success = deliverPrompt(agent, prompt, project);
                    if (success) {
                        return true;
                    }
                    LOG.debug("CxFix-Generic: Agent " + agent.getId() + " delivery returned false, trying next agent");
                } catch (Exception e) {
                    LOG.warn("CxFix-Generic: Agent " + agent.getId() + " threw exception, trying next agent", e);
                }
            }
        }

        LOG.debug("CxFix-Generic: No AI agent available");
        return false;
    }

    // ==================== Detection (config-driven) ====================

    /**
     * Checks if the agent's JetBrains plugin is installed and its UI is reachable.
     * Uses tool window IDs and action IDs from the agent's detection config.
     */
    private static boolean isAvailable(@NotNull AgentDefinition agent, @NotNull Project project) {
        AgentDefinition.DetectionConfig det = agent.getDetection();
        if (det == null) return false;

        // Check tool windows
        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        for (String twId : det.getToolWindowIds()) {
            if (twm.getToolWindow(twId) != null) {
                LOG.debug("CxFix-Generic: " + agent.getId() + " detected via tool window: " + twId);
                return true;
            }
        }

        // Check actions
        ActionManager am = ActionManager.getInstance();
        for (String actionId : det.getActionIds()) {
            if (am.getAction(actionId) != null) {
                LOG.debug("CxFix-Generic: " + agent.getId() + " detected via action: " + actionId);
                return true;
            }
        }

        return false;
    }

    // ==================== Prompt Delivery Pipeline ====================

    /**
     * Full prompt delivery pipeline for a single agent definition.
     */
    private static boolean deliverPrompt(@NotNull AgentDefinition agent, @NotNull String prompt, @NotNull Project project) {
        // Step 1: Always copy to clipboard first (guaranteed fallback)
        copyToClipboard(prompt);
        LOG.debug("CxFix-Generic: Prompt copied to clipboard for " + agent.getId());

        // Step 2: Open chat window (config-driven)
        boolean chatOpened = ApplicationManager.getApplication().runReadAction(
                (Computable<Boolean>) () -> openChat(agent, project));

        if (!chatOpened) {
            LOG.warn("CxFix-Generic: Failed to open chat for " + agent.getId());
            return false;
        }

        // Step 3: Schedule async automation sequence
        return scheduleAutomation(agent, prompt, project);
    }

    // ==================== Chat Opening (config-driven) ====================

    /**
     * Opens the agent's chat window using the configured tool window IDs or action IDs.
     */
    private static boolean openChat(@NotNull AgentDefinition agent, @NotNull Project project) {
        AgentDefinition.ChatConfig chat = agent.getChat();
        if (chat == null) return false;

        // Try tool window activation first
        if (chat.isOpenViaToolWindow()) {
            ToolWindowManager twm = ToolWindowManager.getInstance(project);
            for (String twId : chat.getPreferredToolWindowIds()) {
                ToolWindow tw = twm.getToolWindow(twId);
                if (tw != null) {
                    LOG.debug("CxFix-Generic: Opening chat via tool window: " + twId);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        tw.show();
                        tw.activate(null);
                    });
                    return true;
                }
            }
        }

        // Try action invocation
        if (chat.isOpenViaAction()) {
            ActionManager am = ActionManager.getInstance();
            for (String actionId : chat.getPreferredActionIds()) {
                AnAction action = am.getAction(actionId);
                if (action != null) {
                    LOG.debug("CxFix-Generic: Opening chat via action: " + actionId);
                    ApplicationManager.getApplication().invokeLater(() -> invokeAction(action, project));
                    return true;
                }
            }
        }

        return false;
    }

    // ==================== Async Automation Sequence ====================

    /**
     * Schedules the automated prompt entry sequence (runs async to avoid blocking EDT).
     */
    private static boolean scheduleAutomation(@NotNull AgentDefinition agent, @NotNull String prompt, @NotNull Project project) {
        AtomicBoolean result = new AtomicBoolean(false);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                AgentDefinition.TimingConfig timing = agent.getTiming();

                // Wait for chat UI to fully render
                int openDelay = resolveDelay(timing, "open", timing.getOpenDelayMs());
                TimeUnit.MILLISECONDS.sleep(openDelay);

                // Agent mode switch (config-driven)
                AgentDefinition.AgentModeConfig modeConfig = agent.getAgentMode();
                if (modeConfig != null && modeConfig.isRequired()) {
                    boolean modeSwitched = switchAgentMode(modeConfig, agent, project, timing);
                    if (!modeSwitched) {
                        LOG.warn("CxFix-Generic: Failed to switch to agent mode for " + agent.getId());
                        // Continue anyway - prompt is in clipboard
                    }
                    int modeDelay = resolveDelay(timing, "mode", timing.getModeSwitchDelayMs());
                    if (modeDelay > 0) {
                        TimeUnit.MILLISECONDS.sleep(modeDelay);
                    }
                }

                // Find input field and send prompt (must run on EDT)
                AtomicBoolean sendSuccess = new AtomicBoolean(false);
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    try {
                        ToolWindow tw = findToolWindow(agent, project);
                        if (tw == null) {
                            LOG.warn("CxFix-Generic: Tool window lost after mode switch for " + agent.getId());
                            return;
                        }

                        // Find input field (config-driven)
                        JTextComponent inputField = findInputField(agent.getInput(), tw);
                        if (inputField == null) {
                            LOG.warn("CxFix-Generic: Could not find input field for " + agent.getId());
                            return;
                        }

                        // Set prompt text
                        inputField.setText(prompt);
                        inputField.requestFocusInWindow();
                        LOG.debug("CxFix-Generic: Prompt text set for " + agent.getId());

                        // Send message (config-driven)
                        boolean sent = sendMessage(agent.getSend(), tw, inputField);
                        if (sent) {
                            LOG.debug("CxFix-Generic: Message sent for " + agent.getId());
                            sendSuccess.set(true);
                        } else {
                            LOG.warn("CxFix-Generic: Failed to send message for " + agent.getId());
                        }
                    } catch (Exception e) {
                        LOG.warn("CxFix-Generic: Error during text entry for " + agent.getId(), e);
                    }
                });

                result.set(sendSuccess.get());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("CxFix-Generic: Automation interrupted for " + agent.getId());
            } catch (Exception e) {
                LOG.warn("CxFix-Generic: Automation error for " + agent.getId(), e);
            }
        });

        try {
            future.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.warn("CxFix-Generic: Automation timed out for " + agent.getId(), e);
        }

        // Even if send failed, if chat was opened it's partial success (prompt is in clipboard)
        return result.get();
    }

    // ==================== Agent Mode Switching (config-driven) ====================

    /**
     * Switches to agent/agentic mode using the configured strategy.
     */
    private static boolean switchAgentMode(
            @NotNull AgentDefinition.AgentModeConfig mode,
            @NotNull AgentDefinition agent,
            @NotNull Project project,
            @NotNull AgentDefinition.TimingConfig timing) {

        String strategy = mode.getStrategy();
        if ("none".equalsIgnoreCase(strategy)) {
            return true;
        }

        AtomicBoolean success = new AtomicBoolean(false);

        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow tw = findToolWindow(agent, project);
                if (tw == null) {
                    LOG.warn("CxFix-Generic: Tool window not found for mode switch: " + agent.getId());
                    return;
                }

                switch (strategy.toLowerCase()) {
                    case "combo_box_popup":
                        success.set(switchViaComboBoxPopup(mode, tw, timing));
                        break;
                    case "button_click":
                        success.set(switchViaButtonClick(mode, tw));
                        break;
                    default:
                        LOG.warn("CxFix-Generic: Unknown agent mode strategy: " + strategy);
                }
            } catch (Exception e) {
                LOG.warn("CxFix-Generic: Error during mode switch for " + agent.getId(), e);
            }
        });

        return success.get();
    }

    /**
     * Switches mode via combo box popup interaction.
     * Uses config patterns to find the combo box and the target mode item.
     * Replicates the CopilotIntegration popup simulation approach but fully parameterized.
     */
    @SuppressWarnings("unchecked")
    private static boolean switchViaComboBoxPopup(
            @NotNull AgentDefinition.AgentModeConfig mode,
            @NotNull ToolWindow tw,
            @NotNull AgentDefinition.TimingConfig timing) {

        // Find combo box by class name patterns
        JComboBox<Object> comboBox = findComboBoxByPatterns(tw, mode.getComboBoxClassPatterns());
        if (comboBox == null) {
            LOG.warn("CxFix-Generic: ChatMode combo box not found with patterns: " + mode.getComboBoxClassPatterns());
            return tryFallbackModeSwitch(mode, tw);
        }

        // Find the target mode item index
        int targetIndex = findTargetModeIndex(comboBox, mode);
        if (targetIndex < 0) {
            LOG.warn("CxFix-Generic: Target mode '" + mode.getTargetModeName() + "' not found in combo box");
            return tryFallbackModeSwitch(mode, tw);
        }

        // Simulate full popup interaction sequence (open → select → close)
        try {
            // Open popup
            comboBox.showPopup();
            sleep(timing.getPopupOpenDelayMs());

            // Select the target item
            comboBox.setSelectedIndex(targetIndex);
            sleep(timing.getPopupSelectDelayMs());

            // Close popup
            comboBox.hidePopup();
            sleep(timing.getPopupCloseDelayMs());

            LOG.debug("CxFix-Generic: Agent mode set via combo box popup at index " + targetIndex);
            return true;
        } catch (Exception e) {
            LOG.warn("CxFix-Generic: Combo box popup interaction failed", e);
            return tryFallbackModeSwitch(mode, tw);
        }
    }

    /**
     * Finds the combo box index that matches the target mode name or ID pattern.
     * Uses reflection to extract display names from combo box items.
     */
    private static int findTargetModeIndex(@NotNull JComboBox<Object> comboBox, @NotNull AgentDefinition.AgentModeConfig mode) {
        String targetName = mode.getTargetModeName();
        String targetIdPattern = mode.getTargetModeIdPattern();
        List<String> methodNames = mode.getReflectionMethodNames();

        for (int i = 0; i < comboBox.getItemCount(); i++) {
            Object item = comboBox.getItemAt(i);
            if (item == null) continue;

            String itemStr = item.toString();

            // Check toString() for ID pattern match
            if (targetIdPattern != null && !targetIdPattern.isEmpty() && itemStr.contains(targetIdPattern)) {
                return i;
            }

            // Try reflection methods to extract display name
            String displayName = extractDisplayName(item, methodNames);
            if (displayName != null && displayName.toLowerCase().contains(targetName.toLowerCase())) {
                return i;
            }

            // Check toString() for name match
            if (itemStr.toLowerCase().contains(targetName.toLowerCase())) {
                return i;
            }
        }

        // Fallback to configured index
        int fallbackIndex = mode.getFallbackComboIndex();
        if (fallbackIndex >= 0 && fallbackIndex < comboBox.getItemCount()) {
            LOG.debug("CxFix-Generic: Using fallback combo index: " + fallbackIndex);
            return fallbackIndex;
        }

        return -1;
    }

    /**
     * Extracts a display name from an object using reflection.
     * Tries each configured method name in order until one succeeds.
     */
    @Nullable
    private static String extractDisplayName(@NotNull Object item, @NotNull List<String> methodNames) {
        Class<?> clazz = item.getClass();

        // Try methods
        for (String methodName : methodNames) {
            try {
                Method method = clazz.getMethod(methodName);
                Object result = method.invoke(item);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception ignored) {
                // Method not found or failed -- try next
            }
        }

        // Try fields as last resort
        for (String fieldName : methodNames) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(item);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception ignored) {
                // Field not found -- try next
            }
        }

        return null;
    }

    /**
     * Switches mode via button click.
     * Searches for a button matching the configured text patterns.
     */
    private static boolean switchViaButtonClick(@NotNull AgentDefinition.AgentModeConfig mode, @NotNull ToolWindow tw) {
        for (String pattern : mode.getButtonTextPatterns()) {
            AbstractButton button = findButtonByText(tw, pattern);
            if (button != null && button.isEnabled()) {
                button.doClick();
                LOG.debug("CxFix-Generic: Agent mode set via button click: " + pattern);
                return true;
            }
        }
        LOG.warn("CxFix-Generic: No agent mode button found with patterns: " + mode.getButtonTextPatterns());
        return false;
    }

    /**
     * Attempts the fallback mode switch strategy when the primary strategy fails.
     */
    private static boolean tryFallbackModeSwitch(@NotNull AgentDefinition.AgentModeConfig mode, @NotNull ToolWindow tw) {
        String fallback = mode.getFallbackStrategy();
        if ("button_click".equalsIgnoreCase(fallback)) {
            return switchViaButtonClick(mode, tw);
        }
        return false;
    }

    // ==================== Input Field Finding (config-driven) ====================

    /**
     * Finds the text input component in the agent's chat panel.
     */
    @Nullable
    private static JTextComponent findInputField(@Nullable AgentDefinition.InputConfig inputCfg, @NotNull ToolWindow tw) {
        // Default strategy: recursive Swing traversal for JTextComponent
        Content[] contents = tw.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                JTextComponent found = findTextComponentRecursively(component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Recursively searches the Swing component tree for a JTextComponent (text field or text area).
     */
    @Nullable
    private static JTextComponent findTextComponentRecursively(@NotNull Component component) {
        if (component instanceof JTextComponent) {
            JTextComponent textComp = (JTextComponent) component;
            // Prefer editable, visible text components
            if (textComp.isEditable() && textComp.isVisible() && textComp.isEnabled()) {
                return textComp;
            }
        }

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

    // ==================== Message Sending (config-driven) ====================

    /**
     * Sends the message using the configured strategies in order.
     */
    private static boolean sendMessage(@Nullable AgentDefinition.SendConfig sendCfg, @NotNull ToolWindow tw, @NotNull JTextComponent inputField) {
        if (sendCfg == null || sendCfg.getStrategies().isEmpty()) {
            // Default: try Enter key
            return simulateEnterKey(inputField);
        }

        for (String strategy : sendCfg.getStrategies()) {
            switch (strategy.toLowerCase()) {
                case "button_click":
                    AbstractButton sendBtn = findSendButton(sendCfg, tw);
                    if (sendBtn != null && sendBtn.isEnabled()) {
                        LOG.debug("CxFix-Generic: Sending via button click");
                        sendBtn.doClick();
                        return true;
                    }
                    break;

                case "enter_key":
                    LOG.debug("CxFix-Generic: Sending via Enter key simulation");
                    return simulateEnterKey(inputField);

                default:
                    LOG.warn("CxFix-Generic: Unknown send strategy: " + strategy);
            }
        }

        // Last resort: Enter key
        return simulateEnterKey(inputField);
    }

    /**
     * Finds a send/submit button matching the configured patterns.
     */
    @Nullable
    private static AbstractButton findSendButton(@NotNull AgentDefinition.SendConfig sendCfg, @NotNull ToolWindow tw) {
        Content[] contents = tw.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                AbstractButton found = findSendButtonRecursively(component, sendCfg);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Recursively searches for a button matching any of the configured text, tooltip, or class patterns.
     */
    @Nullable
    private static AbstractButton findSendButtonRecursively(@NotNull Component component, @NotNull AgentDefinition.SendConfig sendCfg) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            if (matchesButtonPatterns(button, sendCfg)) {
                return button;
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                AbstractButton found = findSendButtonRecursively(child, sendCfg);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a button matches any of the configured text, tooltip, or class name patterns.
     */
    private static boolean matchesButtonPatterns(@NotNull AbstractButton button, @NotNull AgentDefinition.SendConfig sendCfg) {
        // Check button text
        String text = button.getText();
        if (text != null && !text.isEmpty()) {
            String lowerText = text.toLowerCase();
            for (String pattern : sendCfg.getButtonTextPatterns()) {
                if (lowerText.contains(pattern.toLowerCase())) {
                    return true;
                }
            }
        }

        // Check tooltip
        String tooltip = button.getToolTipText();
        if (tooltip != null && !tooltip.isEmpty()) {
            String lowerTooltip = tooltip.toLowerCase();
            for (String pattern : sendCfg.getButtonTooltipPatterns()) {
                if (lowerTooltip.contains(pattern.toLowerCase())) {
                    return true;
                }
            }
        }

        // Check class name
        String className = button.getClass().getSimpleName().toLowerCase();
        for (String pattern : sendCfg.getButtonClassPatterns()) {
            if (className.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    // ==================== Utility Methods ====================

    /**
     * Finds a tool window for the given agent in the current project.
     */
    @Nullable
    private static ToolWindow findToolWindow(@NotNull AgentDefinition agent, @NotNull Project project) {
        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        List<String> ids = agent.getDetection() != null
                ? agent.getDetection().getToolWindowIds()
                : (agent.getChat() != null ? agent.getChat().getPreferredToolWindowIds() : List.of());

        for (String twId : ids) {
            ToolWindow tw = twm.getToolWindow(twId);
            if (tw != null) return tw;
        }
        return null;
    }

    /**
     * Finds a JComboBox whose class name matches any of the given patterns.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static JComboBox<Object> findComboBoxByPatterns(@NotNull ToolWindow tw, @NotNull List<String> classPatterns) {
        Content[] contents = tw.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                JComboBox<Object> found = findComboBoxRecursively(component, classPatterns);
                if (found != null) return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static JComboBox<Object> findComboBoxRecursively(@NotNull Component component, @NotNull List<String> classPatterns) {
        if (component instanceof JComboBox) {
            String className = component.getClass().getSimpleName();
            for (String pattern : classPatterns) {
                if (className.contains(pattern)) {
                    return (JComboBox<Object>) component;
                }
            }
            // Also check items for matching type names
            JComboBox<Object> combo = (JComboBox<Object>) component;
            if (combo.getItemCount() > 0) {
                Object firstItem = combo.getItemAt(0);
                if (firstItem != null) {
                    String itemClassName = firstItem.getClass().getSimpleName();
                    for (String pattern : classPatterns) {
                        if (itemClassName.contains(pattern)) {
                            return combo;
                        }
                    }
                }
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JComboBox<Object> found = findComboBoxRecursively(child, classPatterns);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Finds a button in the tool window whose text contains the given text.
     */
    @Nullable
    private static AbstractButton findButtonByText(@NotNull ToolWindow tw, @NotNull String textPattern) {
        Content[] contents = tw.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                AbstractButton found = findButtonByTextRecursively(component, textPattern);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private static AbstractButton findButtonByTextRecursively(@NotNull Component component, @NotNull String textPattern) {
        if (component instanceof AbstractButton) {
            AbstractButton button = (AbstractButton) component;
            String text = button.getText();
            String tooltip = button.getToolTipText();
            if ((text != null && text.toLowerCase().contains(textPattern.toLowerCase())) ||
                    (tooltip != null && tooltip.toLowerCase().contains(textPattern.toLowerCase()))) {
                return button;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                AbstractButton found = findButtonByTextRecursively(child, textPattern);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Copies text to the system clipboard (must be called on EDT or in invokeAndWait).
     */
    private static void copyToClipboard(@NotNull String text) {
        try {
            ApplicationManager.getApplication().invokeAndWait(() ->
                    CopyPasteManager.getInstance().setContents(new StringSelection(text)));
        } catch (Exception e) {
            LOG.warn("CxFix-Generic: Failed to copy to clipboard", e);
        }
    }

    /**
     * Simulates pressing the Enter key in the given text component.
     */
    private static boolean simulateEnterKey(@NotNull JTextComponent inputField) {
        try {
            KeyEvent enterPressed = new KeyEvent(
                    inputField, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                    0, KeyEvent.VK_ENTER, '\n');
            KeyEvent enterReleased = new KeyEvent(
                    inputField, KeyEvent.KEY_RELEASED, System.currentTimeMillis(),
                    0, KeyEvent.VK_ENTER, '\n');
            inputField.dispatchEvent(enterPressed);
            inputField.dispatchEvent(enterReleased);
            return true;
        } catch (Exception e) {
            LOG.warn("CxFix-Generic: Failed to simulate Enter key", e);
            return false;
        }
    }

    /**
     * Invokes an IntelliJ action programmatically.
     */
    private static void invokeAction(@NotNull AnAction action, @NotNull Project project) {
        try {
            DataContext dataContext = dataId -> {
                if (CommonDataKeys.PROJECT.is(dataId)) return project;
                return null;
            };
            AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        } catch (Exception e) {
            LOG.warn("CxFix-Generic: Failed to invoke action", e);
        }
    }

    /**
     * Resolves a timing delay with system property override support.
     */
    private static int resolveDelay(@Nullable AgentDefinition.TimingConfig timing, @NotNull String name, int defaultMs) {
        if (timing != null) {
            String prefix = timing.getSystemPropertyPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                Integer override = Integer.getInteger(prefix + "." + name);
                if (override != null) {
                    return override;
                }
            }
        }
        return defaultMs;
    }

    /**
     * Thread.sleep wrapper that swallows InterruptedException logging.
     */
    private static void sleep(int millis) {
        if (millis <= 0) return;
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
