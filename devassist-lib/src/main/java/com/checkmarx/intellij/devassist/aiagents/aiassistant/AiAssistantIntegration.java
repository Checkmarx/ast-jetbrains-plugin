package com.checkmarx.intellij.devassist.aiagents.aiassistant;

import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.aiagents.ChatIntegrationResult;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utility class for integrating with JetBrains AI Assistant chat
 */
public final class AiAssistantIntegration {

    private static final Logger LOGGER = Utils.getLogger(AiAssistantIntegration.class);

    private static final PluginId AI_ASSISTANT_PLUGIN_ID = PluginId.getId("com.intellij.ml.llm");

    /**
     * ID of AI Assistant's own "Model Context Protocol (MCP)" project settings page
     */
    public static final String AI_ASSISTANT_MCP_CONFIGURABLE_ID = "ml.llm.mcp";

    /**
     * Candidate tool window IDs for the AI Assistant chat panel. Checked in order since the
     * exact ID is not part of AI Assistant's public/documented API and may vary by IDE version.
     */
    private static final List<String> AI_ASSISTANT_TOOL_WINDOW_IDS = List.of(
            "AI Assistant", "AIAssistant", "AI Chat", "AIChat"
    );

    /**
     * Non-internal action (declared in AI Assistant's plugin.xml) that starts a fresh chat.
     */
    private static final String NEW_CHAT_ACTION_ID = "AIAssistant.ToolWindow.NewChatActionAlt";

    /**
     * The chat input's own "Send" toolbar action (sits next to the input field in
     * AI Assistant's plugin.xml, group {@code AIAssistant.Chat.Input.Right}). Used to submit
     * the pasted prompt, since there is no documented "submit chat message" API.
     */
    private static final String SEND_ACTION_ID = "AIAssistant.Chat.SendActions.Send";

    private static final String PASTE_ACTION_ID = "$Paste";

    private static final String ACTION_PLACE = "CxOneAssist.FixWithAI";

    /**
     * Maximum time to wait for focus to move to the AI Assistant chat input after activation.
     * Fallback timeout for when focus listener doesn't confirm focus moved in time.
     * Overridable via {@code -Dcx.aiassistant.delay.paste=<ms>} for troubleshooting.
     */
    private static final int PASTE_READINESS_TIMEOUT_MS = Integer.getInteger("cx.aiassistant.delay.paste", 900);

    /**
     * Poll interval (milliseconds) when checking if focus has moved to the input field.
     * Shorter intervals detect focus changes faster but consume more CPU.
     */
    private static final int FOCUS_POLL_INTERVAL_MS = 50;

    /**
     * Maximum time to wait for the chat input's document to settle after pasting.
     * Fallback timeout for when content-change listener doesn't confirm change in time.
     * Overridable via {@code -Dcx.aiassistant.delay.send=<ms>}.
     */
    private static final int SEND_READINESS_TIMEOUT_MS = Integer.getInteger("cx.aiassistant.delay.send", 300);

    /**
     * Poll interval (milliseconds) when checking if the pasted content is in the input field.
     */
    private static final int CONTENT_POLL_INTERVAL_MS = 30;

    /**
     * Number of times the fallback path (no matching tool-window id) polls for the tool window
     * to appear before giving up and firing the "new chat" action blind, at
     * {@link #FALLBACK_POLL_INTERVAL_MS} apart.
     */
    private static final int FALLBACK_POLL_ATTEMPTS = 10;
    private static final int FALLBACK_POLL_INTERVAL_MS = 200;

    /**
     * Incremented once per call to {@link #openAiAssistantWithPromptDetailed}. Every scheduled
     * step (new-chat action, paste Timer, send Timer) captures the generation active when it was
     * scheduled and checks it's still current before acting, so that clicking "Fix with AI"
     * again while a previous automation chain is still in flight cancels the stale chain instead
     * of letting both race over the same clipboard/focus/chat-input state.
     */
    private static final AtomicInteger CURRENT_GENERATION = new AtomicInteger(0);

    /**
     * Minimum IDE marketing version (e.g. {@code "2025.3.2"}, as returned by
     * {@link ApplicationInfo#getFullVersion()}) known to ship an AI Assistant compatible with the
     * Agent Client Protocol (ACP), compared via {@link VersionComparatorUtil}.
     */
    private static final String MIN_ACP_IDE_VERSION = "2025.3.3";

    private AiAssistantIntegration() {
        // Utility class
    }

    /**
     * Checks whether JetBrains AI Assistant is installed and enabled, based solely on its plugin
     * descriptor.
     */
    public static boolean isAiAssistantAvailable(@Nullable Project project) {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(AI_ASSISTANT_PLUGIN_ID);
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Whether the current IDE version is recent enough to support the Agent Client Protocol
     * (ACP).
     */
    public static boolean supportsAcp() {
        String currentVersion = ApplicationInfo.getInstance().getFullVersion();
        return VersionComparatorUtil.compare(currentVersion, MIN_ACP_IDE_VERSION) >= 0;
    }

    /**
     * Copies the prompt to the clipboard, opens/focuses the AI Assistant chat, and best-effort
     * pastes the prompt into the now-focused chat input.
     *
     * @param prompt               the fix/explanation prompt to send
     * @param project              the project context
     * @param completionCallback   optional callback invoked with the final result after paste+send completes;
     *                             if null, no async result tracking occurs
     * @return a preliminary result describing whether the chat was opened; when a completion callback
     *         is provided, the final result (reflecting paste+send success/failure) is delivered
     *         asynchronously via the callback
     */
    public static ChatIntegrationResult openAiAssistantWithPromptDetailed(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        LOGGER.debug("CxFix: Starting AI Assistant integration workflow");

        // Supersede any automation chain from a previous call that may still be in flight
        int generation = CURRENT_GENERATION.incrementAndGet();

        if (!copyToClipboard(prompt)) {
            return ChatIntegrationResult.notAvailable("Failed to copy prompt to clipboard.");
        }
        LOGGER.debug("CxFix: Prompt copied to clipboard");

        if (!isAiAssistantAvailable(project)) {
            LOGGER.debug("CxFix: AI Assistant not available, prompt copied to clipboard");
            return ChatIntegrationResult.notAvailable(
                    "JetBrains AI Assistant is not installed or available. The fix prompt has been copied to your clipboard.");
        }

        boolean opened = tryOpenAiAssistantChat(project, prompt, generation, completionCallback);
        if (!opened) {
            LOGGER.warn("CxFix: Failed to open AI Assistant chat window");
            return ChatIntegrationResult.notAvailable(
                    "Could not open AI Assistant chat. The fix prompt has been copied to your clipboard.");
        }

        return ChatIntegrationResult.success(
                "AI Assistant chat opened. The prompt was copied to the clipboard and an automated paste + send was "
                        + "attempted - if it does not appear in the chat, paste manually from the clipboard and send.");
    }

    // ==================== Internal helpers ====================

    private static boolean tryOpenAiAssistantChat(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        ToolWindow toolWindow = findToolWindow(project);

        if (toolWindow == null) {
            boolean actionExists = ActionManager.getInstance().getAction(NEW_CHAT_ACTION_ID) != null;
            if (actionExists) {
                pollForToolWindowThenProceed(project, prompt, generation, completionCallback, FALLBACK_POLL_ATTEMPTS);
            }
            return actionExists;
        }

        ToolWindow finalToolWindow = toolWindow;
        ApplicationManager.getApplication().invokeLater(() -> onShowToolWindowRequested(finalToolWindow, project, prompt, generation, completionCallback));
        return true;
    }

    /**
     * Body of the main path's {@code invokeLater} callback, extracted to its own named method so
     * the generation-guard logic is directly unit-testable via reflection.
     */
    private static void onShowToolWindowRequested(
            @NotNull ToolWindow toolWindow,
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant tool-window show superseded by a newer Fix invocation");
            return;
        }
        toolWindow.show(() -> toolWindow.activate(() -> {
            LOGGER.debug("CxFix: AI Assistant tool window activated");
            startNewChatAndPaste(project, prompt, generation, completionCallback);
        }));
    }

    private static ToolWindow findToolWindow(@NotNull Project project) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        for (String id : AI_ASSISTANT_TOOL_WINDOW_IDS) {
            ToolWindow candidate = manager.getToolWindow(id);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Polls for the tool window to appear, checking once per {@link #FALLBACK_POLL_INTERVAL_MS}
     * for up to {@code attemptsRemaining} attempts. If it appears, proceeds via the same
     * show()/activate()-confirmed sequencing as the main path; if it never appears, falls back to
     * firing the "new chat" action directly as a last resort.
     */
    private static void pollForToolWindowThenProceed(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback,
            int attemptsRemaining) {
        Timer timer = new Timer(FALLBACK_POLL_INTERVAL_MS,
                e -> onPollTimerFired(project, prompt, generation, completionCallback, attemptsRemaining));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Body of {@link #pollForToolWindowThenProceed}'s Timer callback, extracted to its own named
     * method so the generation-guard logic is directly unit-testable via reflection without
     * waiting for a real {@link #FALLBACK_POLL_INTERVAL_MS}-delayed Timer to fire.
     */
    private static void onPollTimerFired(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback,
            int attemptsRemaining) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant tool-window poll superseded by a newer Fix invocation");
            return;
        }
        ToolWindow toolWindow = findToolWindow(project);
        if (toolWindow != null) {
            toolWindow.show(() -> toolWindow.activate(() -> {
                LOGGER.debug("CxFix: AI Assistant tool window appeared during fallback poll");
                startNewChatAndPaste(project, prompt, generation, completionCallback);
            }));
        } else if (attemptsRemaining > 1) {
            pollForToolWindowThenProceed(project, prompt, generation, completionCallback, attemptsRemaining - 1);
        } else {
            LOGGER.debug("CxFix: AI Assistant tool window never appeared - firing 'new chat' action without activation confirmation");
            invokeActionWithContext(NEW_CHAT_ACTION_ID, currentFocusDataContext(project));
            schedulePaste(project, prompt, generation, completionCallback);
        }
    }

    /**
     * Starts a brand-new chat every time (rather than pasting into whatever chat happened to
     * already be open) by invoking {@link #NEW_CHAT_ACTION_ID}, then pastes into that fresh
     * chat's input.
     * <p>
     * Must run only after the tool window is confirmed shown/activated - invoking this action
     * beforehand risks it silently no-op'ing if its {@code update()} requires the chat tool
     * window to already be visible/focused. It's also given the real focus-derived
     * {@link DataContext} (see {@link #pasteIntoFocusedChat}), not just the project, since a
     * project-only context may not carry whatever component/editor key the action needs.
     */
    private static void startNewChatAndPaste(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant chat activation superseded by a newer Fix invocation");
            return;
        }
        if (!invokeActionWithContext(NEW_CHAT_ACTION_ID, currentFocusDataContext(project))) {
            LOGGER.debug("CxFix: AI Assistant 'new chat' action unavailable - continuing with whichever chat is open");
        }
        schedulePaste(project, prompt, generation, completionCallback);
    }

    /**
     * Builds a {@link DataContext} from whatever component currently holds keyboard focus (the
     * chat tool window, once activated), falling back to a project-only context if nothing is
     * focused yet.
     */
    private static DataContext currentFocusDataContext(@NotNull Project project) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner != null) {
            return DataManager.getInstance().getDataContext(focusOwner);
        }
        return dataId -> CommonDataKeys.PROJECT.is(dataId) ? project : null;
    }

    /**
     * Waits {@link #PASTE_DELAY_MS} after activation before pasting, since tool-window
     * "activated" fires before AI Assistant finishes moving keyboard focus into its own
     * chat input.
     */
    /**
     * Waits for keyboard focus to move to the AI Assistant chat input, up to the readiness timeout.
     * Uses polling to check focus since focus transfers are async and may not fire listeners reliably.
     *
     * @return true if focus moved to a component (indicating chat input is ready), false if timeout
     */
    private static boolean waitForInputFocus(int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner != null && !(focusOwner instanceof Frame) && !(focusOwner instanceof Window)) {
                // Focus moved to a text input (not a window/frame), indicating chat is ready
                LOGGER.debug("CxFix: Focus moved to input field");
                return true;
            }
            try {
                Thread.sleep(FOCUS_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        LOGGER.debug("CxFix: Timeout waiting for input focus");
        return false;
    }

    /**
     * Waits for content to appear in the focused text component, indicating paste succeeded.
     * Uses polling to check document length since paste operations are async.
     *
     * @param focusOwner the component that should receive the paste
     * @param expectedContent substring that should appear after paste (typically part of the prompt)
     * @param timeoutMs maximum time to wait
     * @return true if expected content found, false if timeout
     */
    private static boolean waitForPasteContent(@Nullable Component focusOwner, @NotNull String expectedContent, int timeoutMs) {
        if (!(focusOwner instanceof JTextComponent)) {
            return true; // Non-text component, can't verify but assume success
        }
        JTextComponent textField = (JTextComponent) focusOwner;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            String currentText = textField.getText();
            if (currentText.contains(expectedContent)) {
                LOGGER.debug("CxFix: Pasted content verified in input field");
                return true;
            }
            try {
                Thread.sleep(CONTENT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        LOGGER.debug("CxFix: Timeout waiting for paste content verification");
        return false;
    }

    private static void schedulePaste(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        Timer timer = new Timer(PASTE_READINESS_TIMEOUT_MS, e -> onPasteTimerFired(project, prompt, generation, completionCallback));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Body of {@link #schedulePaste}'s Timer callback, extracted to its own named method so the
     * generation-guard logic is directly unit-testable via reflection without waiting for a real
     * {@link #PASTE_READINESS_TIMEOUT_MS}-delayed Timer to fire.
     */
    private static void onPasteTimerFired(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant paste superseded by a newer Fix invocation");
            return;
        }
        pasteIntoFocusedChat(project, prompt, generation, completionCallback);
    }

    /**
     * Re-copies {@code prompt} to the clipboard, then pastes it into whichever component
     * currently holds keyboard focus, verifying paste succeeded before submitting via {@link #SEND_ACTION_ID}.
     *
     * Verifies readiness at two stages:
     * 1. After paste delay: waits for focus to actually move to the input field
     * 2. After paste: polls for the pasted content to appear in the field
     *
     * If paste fails and a completion callback is provided, reports failure through the callback.
     * If paste succeeds, schedules send and reports result after send completes.
     */
    private static void pasteIntoFocusedChat(
            @NotNull Project project,
            @NotNull String prompt,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        // Verify focus has moved to the input field (after the initial paste delay)
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean focusReady = waitForInputFocus(50); // Quick final check after delay
        if (!focusReady) {
            LOGGER.debug("CxFix: Focus did not move to input field - chat may not be ready");
        }

        focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            LOGGER.debug("CxFix: No focus owner available to paste the prompt into");
            reportPasteFailure(completionCallback);
            return;
        }

        copyToClipboard(prompt);

        DataContext realContext = DataManager.getInstance().getDataContext(focusOwner);

        boolean pasted = invokeActionWithContext(PASTE_ACTION_ID, realContext);

        // Fallback for the (less likely) case that the input is a plain Swing text component.
        if (!pasted && focusOwner instanceof JTextComponent) {
            try {
                ((JTextComponent) focusOwner).paste();
                pasted = true;
                LOGGER.debug("CxFix: Pasted prompt directly into focused AI Assistant chat input");
            } catch (Exception e) {
                LOGGER.debug("CxFix: Direct paste into focused text component failed", e);
            }
        }

        if (!pasted) {
            LOGGER.debug("CxFix: Could not paste prompt into AI Assistant chat input");
            reportPasteFailure(completionCallback);
            return;
        }

        // Verify paste actually succeeded by checking if content appeared in the field
        // Use prompt snippet for verification (first 20 chars or less)
        String verifyContent = prompt.length() > 20 ? prompt.substring(0, 20) : prompt;
        boolean pasteVerified = waitForPasteContent(focusOwner, verifyContent, SEND_READINESS_TIMEOUT_MS / 2);
        if (!pasteVerified) {
            LOGGER.warn("CxFix: Paste verification failed - content not detected in input field");
            reportPasteFailure(completionCallback);
            return;
        }

        Timer sendTimer = new Timer(SEND_READINESS_TIMEOUT_MS, e -> onSendTimerFired(realContext, generation, completionCallback));
        sendTimer.setRepeats(false);
        sendTimer.start();
    }

    /**
     * Reports paste failure through the completion callback if provided.
     */
    private static void reportPasteFailure(@Nullable Consumer<ChatIntegrationResult> completionCallback) {
        if (completionCallback != null) {
            ChatIntegrationResult failureResult = ChatIntegrationResult.notAvailable(
                    "Failed to paste prompt into AI Assistant chat. The prompt has been copied to your clipboard.");
            completionCallback.accept(failureResult);
        }
    }

    /**
     * Body of {@link #pasteIntoFocusedChat}'s send Timer callback, extracted to its own named
     * method so the generation-guard logic is directly unit-testable via reflection without
     * waiting for a real {@link #SEND_READINESS_TIMEOUT_MS}-delayed Timer to fire.
     *
     * Invokes the completion callback with the final result (success or failure) after send completes.
     */
    private static void onSendTimerFired(
            @NotNull DataContext fallbackContext,
            int generation,
            @Nullable Consumer<ChatIntegrationResult> completionCallback) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant send superseded by a newer Fix invocation");
            return;
        }
        Component currentFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        DataContext sendContext = currentFocus != null
                ? DataManager.getInstance().getDataContext(currentFocus)
                : fallbackContext;
        boolean sent = invokeActionWithContext(SEND_ACTION_ID, sendContext);
        if (!sent) {
            LOGGER.debug("CxFix: AI Assistant send action not available - user must press Enter/click Send");
        }

        // Report final result: send success or failure through completion callback
        if (completionCallback != null) {
            ChatIntegrationResult finalResult = sent
                    ? ChatIntegrationResult.success("Prompt sent to AI Assistant")
                    : ChatIntegrationResult.notAvailable("Failed to send prompt to AI Assistant. The prompt has been copied to your clipboard.");
            completionCallback.accept(finalResult);
        }
    }

    private static boolean invokeActionWithContext(String actionId, @NotNull DataContext dataContext) {
        try {
            AnAction action = ActionManager.getInstance().getAction(actionId);
            if (action == null) {
                return false;
            }
            AnActionEvent event = AnActionEvent.createFromDataContext(ACTION_PLACE, null, dataContext);
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
            return true;
        } catch (Exception e) {
            LOGGER.debug("CxFix: AI Assistant action '" + actionId + "' not available or failed", e);
            return false;
        }
    }

    private static boolean copyToClipboard(@NotNull String text) {
        try {
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
}
