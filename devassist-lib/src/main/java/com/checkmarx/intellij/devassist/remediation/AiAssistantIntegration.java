package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for integrating with JetBrains AI Assistant chat
 */
public final class AiAssistantIntegration {

    private static final Logger LOGGER = Utils.getLogger(AiAssistantIntegration.class);

    private static final PluginId AI_ASSISTANT_PLUGIN_ID = PluginId.getId("com.intellij.ml.llm");

    /**
     * ID of AI Assistant's own "Model Context Protocol (MCP)" project settings page (registered
     * as a {@code projectConfigurable} in its plugin.xml under the {@code ml.llm.LLMConfigurable}
     * group). Used to navigate the Settings dialog there directly - via
     * {@link com.intellij.openapi.options.ex.Settings#find(String)} - instead of hand-editing
     * its {@code mcp.json}, since that file's location/schema is not documented public API and
     * the settings UI is the officially supported way for users to inspect/manage it.
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
     * Delay after the chat tool window reports itself "activated" before attempting to paste.
     * AI Assistant transfers keyboard focus to its chat input asynchronously (its own internal
     * UI construction/focus routing runs after our activation callback fires), so pasting
     * immediately on activation races that and silently lands nowhere. Overridable via
     * {@code -Dcx.aiassistant.delay.paste=<ms>} for troubleshooting.
     */
    private static final int PASTE_DELAY_MS = Integer.getInteger("cx.aiassistant.delay.paste", 900);

    /**
     * Delay after pasting before submitting, so the chat input's document/model has settled
     * (and any async validation the input performs on change has run) before Send reads it.
     * Overridable via {@code -Dcx.aiassistant.delay.send=<ms>}.
     */
    private static final int SEND_DELAY_MS = Integer.getInteger("cx.aiassistant.delay.send", 300);

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

    private AiAssistantIntegration() {
        // Utility class
    }

    /**
     * Detailed result with a user-facing message, mirroring {@link CopilotIntegration.IntegrationResult}.
     */
    public static final class IntegrationResult {
        private final boolean success;
        private final String message;

        private IntegrationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        static IntegrationResult success(String message) {
            return new IntegrationResult(true, message);
        }

        static IntegrationResult notAvailable(String message) {
            return new IntegrationResult(false, message);
        }
    }

    /**
     * Checks whether JetBrains AI Assistant is installed/enabled, or its chat tool window is
     * already registered for the given project.
     */
    public static boolean isAiAssistantAvailable(@Nullable Project project) {
        IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(AI_ASSISTANT_PLUGIN_ID);
        if (plugin != null && plugin.isEnabled()) {
            return true;
        }

        if (project != null) {
            ToolWindowManager manager = ToolWindowManager.getInstance(project);
            for (String id : AI_ASSISTANT_TOOL_WINDOW_IDS) {
                if (manager.getToolWindow(id) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Copies the prompt to the clipboard, opens/focuses the AI Assistant chat, and best-effort
     * pastes the prompt into the now-focused chat input.
     *
     * @param prompt  the fix/explanation prompt to send
     * @param project the project context
     * @return a result describing whether the chat was opened; the caller (e.g. {@link RemediationManager})
     * is responsible for falling back to a plain clipboard-copy notification when this is not successful.
     */
    public static IntegrationResult openAiAssistantWithPromptDetailed(@NotNull String prompt, @NotNull Project project) {
        LOGGER.debug("CxFix: Starting AI Assistant integration workflow");

        // Supersede any automation chain from a previous call that may still be in flight
        int generation = CURRENT_GENERATION.incrementAndGet();

        if (!copyToClipboard(prompt)) {
            return IntegrationResult.notAvailable("Failed to copy prompt to clipboard.");
        }
        LOGGER.debug("CxFix: Prompt copied to clipboard");

        if (!isAiAssistantAvailable(project)) {
            LOGGER.debug("CxFix: AI Assistant not available, prompt copied to clipboard");
            return IntegrationResult.notAvailable(
                    "JetBrains AI Assistant is not installed or available. The fix prompt has been copied to your clipboard.");
        }

        boolean opened = tryOpenAiAssistantChat(project, prompt, generation);
        if (!opened) {
            LOGGER.warn("CxFix: Failed to open AI Assistant chat window");
            return IntegrationResult.notAvailable(
                    "Could not open AI Assistant chat. The fix prompt has been copied to your clipboard.");
        }

        return IntegrationResult.success(
                "AI Assistant chat opened. The prompt was copied to the clipboard and an automated paste + send was "
                        + "attempted - if it does not appear in the chat, paste manually from the clipboard and send.");
    }

    // ==================== Internal helpers ====================

    private static boolean tryOpenAiAssistantChat(@NotNull Project project, @NotNull String prompt, int generation) {
        ToolWindow toolWindow = findToolWindow(project);

        if (toolWindow == null) {
            boolean actionExists = ActionManager.getInstance().getAction(NEW_CHAT_ACTION_ID) != null;
            if (actionExists) {
                pollForToolWindowThenProceed(project, prompt, generation, FALLBACK_POLL_ATTEMPTS);
            }
            return actionExists;
        }

        ToolWindow finalToolWindow = toolWindow;
        ApplicationManager.getApplication().invokeLater(() -> onShowToolWindowRequested(finalToolWindow, project, prompt, generation));
        return true;
    }

    /**
     * Body of the main path's {@code invokeLater} callback, extracted to its own named method so
     * the generation-guard logic is directly unit-testable via reflection.
     */
    private static void onShowToolWindowRequested(@NotNull ToolWindow toolWindow, @NotNull Project project,
                                                    @NotNull String prompt, int generation) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant tool-window show superseded by a newer Fix invocation");
            return;
        }
        toolWindow.show(() -> toolWindow.activate(() -> {
            LOGGER.debug("CxFix: AI Assistant tool window activated");
            startNewChatAndPaste(project, prompt, generation);
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
    private static void pollForToolWindowThenProceed(@NotNull Project project, @NotNull String prompt, int generation, int attemptsRemaining) {
        Timer timer = new Timer(FALLBACK_POLL_INTERVAL_MS,
                e -> onPollTimerFired(project, prompt, generation, attemptsRemaining));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Body of {@link #pollForToolWindowThenProceed}'s Timer callback, extracted to its own named
     * method so the generation-guard logic is directly unit-testable via reflection without
     * waiting for a real {@link #FALLBACK_POLL_INTERVAL_MS}-delayed Timer to fire.
     */
    private static void onPollTimerFired(@NotNull Project project, @NotNull String prompt, int generation, int attemptsRemaining) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant tool-window poll superseded by a newer Fix invocation");
            return;
        }
        ToolWindow toolWindow = findToolWindow(project);
        if (toolWindow != null) {
            toolWindow.show(() -> toolWindow.activate(() -> {
                LOGGER.debug("CxFix: AI Assistant tool window appeared during fallback poll");
                startNewChatAndPaste(project, prompt, generation);
            }));
        } else if (attemptsRemaining > 1) {
            pollForToolWindowThenProceed(project, prompt, generation, attemptsRemaining - 1);
        } else {
            LOGGER.debug("CxFix: AI Assistant tool window never appeared - firing 'new chat' action without activation confirmation");
            invokeActionWithContext(NEW_CHAT_ACTION_ID, currentFocusDataContext(project));
            schedulePaste(project, prompt, generation);
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
    private static void startNewChatAndPaste(@NotNull Project project, @NotNull String prompt, int generation) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant chat activation superseded by a newer Fix invocation");
            return;
        }
        if (!invokeActionWithContext(NEW_CHAT_ACTION_ID, currentFocusDataContext(project))) {
            LOGGER.debug("CxFix: AI Assistant 'new chat' action unavailable - continuing with whichever chat is open");
        }
        schedulePaste(project, prompt, generation);
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
    private static void schedulePaste(@NotNull Project project, @NotNull String prompt, int generation) {
        Timer timer = new Timer(PASTE_DELAY_MS, e -> onPasteTimerFired(project, prompt, generation));
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Body of {@link #schedulePaste}'s Timer callback, extracted to its own named method so the
     * generation-guard logic is directly unit-testable via reflection without waiting for a real
     * {@link #PASTE_DELAY_MS}-delayed Timer to fire.
     */
    private static void onPasteTimerFired(@NotNull Project project, @NotNull String prompt, int generation) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant paste superseded by a newer Fix invocation");
            return;
        }
        pasteIntoFocusedChat(project, prompt, generation);
    }

    /**
     * Re-copies {@code prompt} to the clipboard, then pastes it into whichever component
     * currently holds keyboard focus, then submits via {@link #SEND_ACTION_ID}.
     */
    private static void pasteIntoFocusedChat(@NotNull Project project, @NotNull String prompt, int generation) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
            LOGGER.debug("CxFix: No focus owner available to paste the prompt into");
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
            return;
        }

        Timer sendTimer = new Timer(SEND_DELAY_MS, e -> onSendTimerFired(realContext, generation));
        sendTimer.setRepeats(false);
        sendTimer.start();
    }

    /**
     * Body of {@link #pasteIntoFocusedChat}'s send Timer callback, extracted to its own named
     * method so the generation-guard logic is directly unit-testable via reflection without
     * waiting for a real {@link #SEND_DELAY_MS}-delayed Timer to fire.
     */
    private static void onSendTimerFired(@NotNull DataContext fallbackContext, int generation) {
        if (generation != CURRENT_GENERATION.get()) {
            LOGGER.debug("CxFix: AI Assistant send superseded by a newer Fix invocation");
            return;
        }
        Component currentFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        DataContext sendContext = currentFocus != null
                ? DataManager.getInstance().getDataContext(currentFocus)
                : fallbackContext;
        if (!invokeActionWithContext(SEND_ACTION_ID, sendContext)) {
            LOGGER.debug("CxFix: AI Assistant send action not available - user must press Enter/click Send");
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
