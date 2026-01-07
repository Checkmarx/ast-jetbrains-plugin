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
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Utility class for integrating with Augment Code Chat in IntelliJ IDEA.
 *
 * <p>This class provides automated interaction with Augment Code Chat to send fix prompts.
 * <b>Important:</b> Augment Code uses a JCEF webview (embedded browser) for its chat UI,
 * not traditional Swing components. This requires different automation approaches:
 *
 * <h3>Webview-based Automation Strategy:</h3>
 * <ol>
 *   <li>Copy prompt to clipboard (guaranteed fallback)</li>
 *   <li>Open Augment Code Chat tool window</li>
 *   <li>Switch to Agent mode via reflection on Augment's internal services</li>
 *   <li>Focus the webview component</li>
 *   <li>Use keyboard events (Paste + Enter) to interact with the webview</li>
 * </ol>
 *
 * <h3>Fallback Behavior:</h3>
 * <p>If automation fails at any step, the prompt remains in the clipboard and the user is
 * notified to paste manually with Ctrl/Cmd+V.
 */
public final class AugmentIntegration {

    private static final Logger LOGGER = Utils.getLogger(AugmentIntegration.class);

    // ==================== Configuration Constants ====================

    /**
     * Configuration for timing delays in UI automation.
     * These values are tuned for webview-based UI which requires more time to initialize.
     */
    private static final class Timing {
        /** Delay after opening Augment to allow webview to fully render */
        static final int AUGMENT_OPEN_DELAY_MS = 1500;
        
        /** Delay for Agent mode UI to fully load after mode switch */
        static final int AGENT_MODE_DELAY_MS = 1000;
        
        /** Delay after focusing webview before sending keys */
        static final int FOCUS_DELAY_MS = 300;
        
        /** Delay between paste and enter */
        static final int PASTE_DELAY_MS = 200;
        
        /** Delay after clicking a mode toggle button */
        static final int MODE_TOGGLE_DELAY_MS = 500;
    }

    /** Known Augment action IDs for opening the chat window */
    private static final String[] AUGMENT_CHAT_ACTION_IDS = {
            "augment.chat.show",
            "augment.openChat",
            "augment.chat.open",
            "Augment.Chat.Show",
            "augmentcode.chat.show",
            "augmentcode.openChat",
            "Augment.ShowChat",
            "AugmentCode.ShowChat"
    };

    /** Known Augment tool window IDs */
    private static final String[] AUGMENT_TOOL_WINDOW_IDS = {
            "Augment",
            "Augment Code",
            "Augment Chat",
            "AugmentCode"
    };

    /** JCEF browser class names to look for */
    private static final String[] JCEF_BROWSER_CLASS_NAMES = {
            "com.intellij.ui.jcef.JBCefBrowser",
            "com.intellij.ui.jcef.JBCefBrowserBase",
            "org.cef.browser.CefBrowser"
    };

    // ==================== Result Types ====================

    /**
     * Result of an Augment integration operation.
     */
    public enum OperationResult {
        /** Full automation succeeded - prompt was sent to Augment */
        FULL_SUCCESS,
        /** Partial success - Augment opened but automation may have issues */
        PARTIAL_SUCCESS,
        /** Augment not available - prompt copied to clipboard only */
        AUGMENT_NOT_AVAILABLE,
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
        static IntegrationResult augmentNotAvailable(String message) {
            return new IntegrationResult(OperationResult.AUGMENT_NOT_AVAILABLE, message, null);
        }
        static IntegrationResult failed(String message, @Nullable Exception e) {
            return new IntegrationResult(OperationResult.FAILED, message, e);
        }
    }

    private AugmentIntegration() {
        // Prevent instantiation
    }

    // ==================== Public API ====================

    /**
     * Opens Augment chat, switches to agent mode, pastes the prompt, and sends it automatically.
     * This provides a fully automated one-click fix experience.
     *
     * @param prompt  The fix prompt to send to Augment
     * @param project The current project context
     * @return true if the operation was initiated successfully, false otherwise
     */
    public static boolean openAugmentWithPrompt(@NotNull String prompt, @NotNull Project project) {
        IntegrationResult result = openAugmentWithPromptDetailed(prompt, project, null);
        return result.isSuccess();
    }

    /**
     * Opens Augment with prompt and provides detailed result via callback.
     *
     * @param prompt   The fix prompt to send to Augment
     * @param project  The current project context
     * @param callback Optional callback to receive the detailed result (called on EDT)
     * @return Immediate result indicating if operation was initiated
     */
    public static IntegrationResult openAugmentWithPromptDetailed(
            @NotNull String prompt,
            @NotNull Project project,
            @Nullable Consumer<IntegrationResult> callback) {

        LOGGER.info("CxFix: Starting Augment integration workflow");

        // Step 1: Always copy to clipboard first (guaranteed fallback)
        if (!copyToClipboard(prompt)) {
            IntegrationResult result = IntegrationResult.failed(
                    "Failed to copy prompt to clipboard", null);
            notifyCallback(callback, result);
            return result;
        }
        LOGGER.info("CxFix: Prompt copied to clipboard");

        // Step 2: Check if Augment is available
        if (!isAugmentAvailable(project)) {
            LOGGER.info("CxFix: Augment not available, prompt copied to clipboard");
            IntegrationResult result = IntegrationResult.augmentNotAvailable(
                    "Augment Code is not installed or available. The fix prompt has been copied to your clipboard.");
            notifyCallback(callback, result);
            return result;
        }

        // Step 3: Try to open Augment chat
        boolean opened = ApplicationManager.getApplication().runReadAction(
                (Computable<Boolean>) () -> tryOpenAugmentChat(project));

        if (!opened) {
            LOGGER.warn("CxFix: Failed to open Augment chat window");
            IntegrationResult result = IntegrationResult.augmentNotAvailable(
                    "Could not open Augment chat. The fix prompt has been copied to your clipboard.");
            notifyCallback(callback, result);
            return result;
        }

        LOGGER.info("CxFix: Augment chat opened, starting automation sequence");

        // Step 4: Schedule the automation sequence
        scheduleAutomatedPromptEntry(project, prompt, callback);

        return IntegrationResult.partialSuccess("Augment chat opened, automation in progress...");
    }

    /**
     * Checks if Augment Code is available in the current IDE.
     *
     * @param project The project context (can be null for global check)
     * @return true if Augment is available, false otherwise
     */
    public static boolean isAugmentAvailable(@Nullable Project project) {
        // Check for tool window
        if (project != null) {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
            for (String toolWindowId : AUGMENT_TOOL_WINDOW_IDS) {
                if (toolWindowManager.getToolWindow(toolWindowId) != null) {
                    return true;
                }
            }
        }

        // Check for actions
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : AUGMENT_CHAT_ACTION_IDS) {
            if (actionManager.getAction(actionId) != null) {
                return true;
            }
        }

        return false;
    }

    // ==================== Automation Implementation ====================

    /**
     * Schedules the automated prompt entry sequence.
     * Since Augment uses a webview, we need to use keyboard-based automation.
     */
    private static void scheduleAutomatedPromptEntry(
            @NotNull Project project,
            @NotNull String prompt,
            @Nullable Consumer<IntegrationResult> callback) {

        CompletableFuture.runAsync(() -> {
            IntegrationResult result;
            try {
                // Wait for Augment webview to fully load
                LOGGER.info("CxFix: Waiting for Augment chat webview to initialize...");
                TimeUnit.MILLISECONDS.sleep(Timing.AUGMENT_OPEN_DELAY_MS);

                // Attempt automation
                boolean success = tryWebviewBasedAutomation(project, prompt);

                if (success) {
                    LOGGER.info("CxFix: Augment automation completed successfully");
                    result = IntegrationResult.fullSuccess(
                            "Fix prompt sent to Augment Agent successfully!");
                } else {
                    // Automation failed - prompt is already in clipboard
                    LOGGER.warn("CxFix: Augment automation incomplete, prompt available in clipboard");
                    result = IntegrationResult.partialSuccess(
                            "Augment opened but automation may need manual completion. " +
                            "The fix prompt is in your clipboard - press Ctrl/Cmd+V to paste if needed.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("CxFix: Augment automation interrupted", e);
                result = IntegrationResult.partialSuccess(
                        "Operation was interrupted. The fix prompt is in your clipboard - please paste manually.");
            } catch (Exception e) {
                LOGGER.warn("CxFix: Augment automation error: " + e.getMessage());
                result = IntegrationResult.partialSuccess(
                        "Automation encountered an error. The fix prompt is in your clipboard - please paste manually.");
            }

            notifyCallback(callback, result);
        });
    }

    /**
     * Performs webview-based automation by finding the JCEF browser and executing JavaScript.
     * 
     * <p>Since Augment uses JCEF (embedded Chromium browser), standard AWT KeyEvents don't work.
     * We must find the actual JBCefBrowser object and execute JavaScript to interact with the DOM.
     * 
     * <p>Strategy:
     * <ol>
     *   <li>Try to switch to Agent mode via Augment's internal services (reflection)</li>
     *   <li>Find the JBCefBrowser object using deep reflection</li>
     *   <li>Execute JavaScript to set input value and trigger send</li>
     *   <li>If JavaScript fails, notify user to paste manually</li>
     * </ol>
     */
    private static boolean tryWebviewBasedAutomation(@NotNull Project project, @NotNull String prompt) {
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<Object> jcefBrowserRef = new AtomicReference<>();

        // Phase 1: Switch to Agent mode and find JCEF browser (must run on EDT)
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                ToolWindow augmentWindow = findAugmentToolWindow(project);
                if (augmentWindow == null) {
                    LOGGER.warn("CxFix: Augment tool window not found");
                    return;
                }

                // Ensure the tool window is active and focused
                augmentWindow.show();
                augmentWindow.activate(null);

                // Debug: Log component hierarchy for troubleshooting
                logAllComponents(augmentWindow);

                // Try to switch to Agent mode using Augment's internal API
                LOGGER.info("CxFix: Attempting to switch to Augment Agent mode...");
                boolean agentModeSet = trySetAgentModeViaReflection(project);
                if (agentModeSet) {
                    LOGGER.info("CxFix: Augment Agent mode switch initiated");
                } else {
                    LOGGER.info("CxFix: Could not programmatically set Agent mode, may already be active");
                }

                // Find the JBCefBrowser object (not just a Component)
                Object jcefBrowser = findJBCefBrowserInToolWindow(augmentWindow);
                if (jcefBrowser != null) {
                    LOGGER.info("CxFix: Found Augment JCEF browser: " + jcefBrowser.getClass().getName());
                    jcefBrowserRef.set(jcefBrowser);
                } else {
                    LOGGER.warn("CxFix: Could not find JBCefBrowser in Augment tool window");
                }
            } catch (Exception e) {
                LOGGER.warn("CxFix: Error during Augment setup: " + e.getMessage());
            }
        });

        // Phase 2: Wait for Agent mode UI to initialize
        LOGGER.info("CxFix: Waiting for Augment Agent mode to initialize...");
        try {
            TimeUnit.MILLISECONDS.sleep(Timing.AGENT_MODE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Phase 3: Execute JavaScript to set prompt and send (must run on EDT)
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                Object jcefBrowser = jcefBrowserRef.get();
                
                // Try to find browser again if not found earlier
                if (jcefBrowser == null) {
                    ToolWindow augmentWindow = findAugmentToolWindow(project);
                    if (augmentWindow != null) {
                        jcefBrowser = findJBCefBrowserInToolWindow(augmentWindow);
                        if (jcefBrowser != null) {
                            LOGGER.info("CxFix: Found Augment JCEF browser on retry: " + jcefBrowser.getClass().getName());
                        }
                    }
                }

                if (jcefBrowser != null) {
                    // Try to execute JavaScript to set the prompt
                    boolean jsSuccess = executeJavaScriptInBrowser(jcefBrowser, prompt);
                    if (jsSuccess) {
                        LOGGER.info("CxFix: JavaScript executed successfully in Augment webview");
                        success.set(true);
                        return;
                    }
                }

                // JavaScript execution failed or browser not found
                LOGGER.warn("CxFix: Could not execute JavaScript in Augment webview");
                LOGGER.info("CxFix: Prompt is in clipboard - user needs to paste manually (Ctrl+V) and press Enter");
                
                // Try to at least focus the tool window
                ToolWindow augmentWindow = findAugmentToolWindow(project);
                if (augmentWindow != null) {
                    augmentWindow.activate(null);
                }
                
            } catch (Exception e) {
                LOGGER.warn("CxFix: Error during Augment JavaScript execution: " + e.getMessage());
            }
        });

        return success.get();
    }

    // ==================== Agent Mode Switching ====================

    /**
     * Attempts to switch to Agent mode using Augment's internal services via reflection.
     * 
     * <p>Based on logs, Augment uses:
     * <ul>
     *   <li>com.augmentcode.intellij.webviews.chat.ChatMessagingService - for chat mode changes</li>
     *   <li>com.augmentcode.intellij.sidecar.SidecarService - for mode communication</li>
     * </ul>
     */
    private static boolean trySetAgentModeViaReflection(@NotNull Project project) {
        try {
            // Try to find Augment's SidecarService
            Class<?> sidecarServiceClass = Class.forName("com.augmentcode.intellij.sidecar.SidecarService");
            
            // Get the service instance
            Method getInstanceMethod = sidecarServiceClass.getMethod("getInstance", Project.class);
            Object sidecarService = getInstanceMethod.invoke(null, project);
            
            if (sidecarService != null) {
                // Try to find a method to change chat mode
                try {
                    // Try changeChatMode method
                    Method changeModeMethod = sidecarServiceClass.getMethod("changeChatMode", String.class);
                    changeModeMethod.invoke(sidecarService, "AGENT");
                    LOGGER.info("CxFix: Called SidecarService.changeChatMode(AGENT)");
                    return true;
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("CxFix: changeChatMode method not found, trying alternatives");
                }

                // Try setChatMode
                try {
                    Method setModeMethod = sidecarServiceClass.getMethod("setChatMode", String.class);
                    setModeMethod.invoke(sidecarService, "AGENT");
                    LOGGER.info("CxFix: Called SidecarService.setChatMode(AGENT)");
                    return true;
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("CxFix: setChatMode method not found");
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("CxFix: Augment SidecarService class not found");
        } catch (Exception e) {
            LOGGER.debug("CxFix: Error accessing Augment services: " + e.getMessage());
        }

        // Try ChatMessagingService
        try {
            Class<?> chatServiceClass = Class.forName("com.augmentcode.intellij.webviews.chat.ChatMessagingService");
            
            Method getInstanceMethod = chatServiceClass.getMethod("getInstance", Project.class);
            Object chatService = getInstanceMethod.invoke(null, project);
            
            if (chatService != null) {
                // Try to change mode
                Method[] methods = chatServiceClass.getMethods();
                for (Method method : methods) {
                    String methodName = method.getName().toLowerCase();
                    if (methodName.contains("mode") || methodName.contains("agent")) {
                        LOGGER.debug("CxFix: Found potential mode method: " + method.getName());
                        // Try calling with "AGENT" parameter if applicable
                        if (method.getParameterCount() == 1) {
                            try {
                                method.invoke(chatService, "AGENT");
                                LOGGER.info("CxFix: Called " + method.getName() + "(AGENT)");
                                return true;
                            } catch (Exception ex) {
                                LOGGER.debug("CxFix: Method " + method.getName() + " failed: " + ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("CxFix: Augment ChatMessagingService class not found");
        } catch (Exception e) {
            LOGGER.debug("CxFix: Error accessing Augment chat service: " + e.getMessage());
        }

        return false;
    }

    // ==================== Webview Component Discovery ====================

    /**
     * Finds the JCEF browser object in the Augment tool window.
     * Returns the JBCefBrowser object (not a Component) for JavaScript execution.
     */
    private static @Nullable Object findJBCefBrowserInToolWindow(@NotNull ToolWindow toolWindow) {
        Content[] contents = toolWindow.getContentManager().getContents();
        for (Content content : contents) {
            JComponent component = content.getComponent();
            if (component != null) {
                Object browser = findJBCefBrowserRecursively(component);
                if (browser != null) {
                    return browser;
                }
            }
        }
        return null;
    }

    /**
     * Recursively searches for a JBCefBrowser object in the component hierarchy.
     * Uses reflection to find browser objects stored as fields in custom panels.
     */
    private static @Nullable Object findJBCefBrowserRecursively(@NotNull Component component) {
        String className = component.getClass().getName();
        
        // Check if this component IS a JBCefBrowser or related
        if (className.contains("JBCef") || className.contains("CefBrowser")) {
            LOGGER.info("CxFix: Found JCEF component directly: " + className);
            return component;
        }

        // Check for browser stored as a field via reflection
        Object browserFromField = findBrowserViaReflection(component);
        if (browserFromField != null) {
            return browserFromField;
        }

        // Recurse into child components
        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                Object found = findJBCefBrowserRecursively(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Uses reflection to find a JBCefBrowser stored as a field in a component.
     * Many webview wrappers store the browser as a private field.
     */
    private static @Nullable Object findBrowserViaReflection(@NotNull Object component) {
        try {
            Class<?> clazz = component.getClass();
            
            // Search through all fields including inherited ones
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    String fieldTypeName = field.getType().getName();
                    String fieldName = field.getName().toLowerCase();
                    
                    // Look for fields that might hold a browser
                    if (fieldTypeName.contains("JBCef") || 
                        fieldTypeName.contains("CefBrowser") ||
                        fieldName.contains("browser") ||
                        fieldName.contains("webview") ||
                        fieldName.contains("jcef")) {
                        
                        field.setAccessible(true);
                        Object fieldValue = field.get(component);
                        
                        if (fieldValue != null) {
                            String valueClassName = fieldValue.getClass().getName();
                            if (valueClassName.contains("JBCef") || valueClassName.contains("CefBrowser")) {
                                LOGGER.info("CxFix: Found JCEF browser via reflection in field: " + field.getName() + 
                                           " of type: " + valueClassName);
                                return fieldValue;
                            }
                            
                            // Recursively check if this field contains a browser
                            Object nested = findBrowserViaReflection(fieldValue);
                            if (nested != null) {
                                return nested;
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            // Ignore reflection errors
            LOGGER.debug("CxFix: Reflection search failed: " + e.getMessage());
        }
        return null;
    }

    // ==================== JavaScript Execution ====================

    /**
     * Executes JavaScript in the JBCefBrowser to set the prompt and send it.
     * 
     * <p>Uses the public CefBrowser interface to avoid JPMS module access issues
     * with internal classes like RemoteBrowser.
     */
    private static boolean executeJavaScriptInBrowser(@NotNull Object jbCefBrowser, @NotNull String prompt) {
        try {
            // Get the CefBrowser interface from JBCefBrowser
            CefBrowser cefBrowser = getCefBrowserInterface(jbCefBrowser);
            if (cefBrowser == null) {
                LOGGER.warn("CxFix: Could not get CefBrowser interface from JBCefBrowser");
                return false;
            }

            LOGGER.info("CxFix: Got CefBrowser interface, implementation: " + cefBrowser.getClass().getName());

            // Escape the prompt for JavaScript (use JSON-like escaping for safety)
            String escapedPrompt = escapeForJavaScript(prompt);
            
            // Build JavaScript without String.format to avoid issues with % characters in prompt
            // JavaScript to find input and set value - supports various input types
            // Augment likely uses a contenteditable div or textarea
            String setPromptJs = 
                "(() => {" +
                "  const PROMPT = " + escapedPrompt + ";" +  // Store prompt in a variable
                "  console.log('CxFix: Looking for Augment input field');" +
                "  " +
                "  // Try contenteditable elements first (common in modern chat UIs)" +
                "  const editables = document.querySelectorAll('[contenteditable=\"true\"]');" +
                "  for (const el of editables) {" +
                "    if (el.offsetParent !== null && getComputedStyle(el).display !== 'none') {" +
                "      console.log('CxFix: Found contenteditable', el);" +
                "      el.focus();" +
                "      el.innerHTML = '';" +  // Clear existing content
                "      el.textContent = PROMPT;" +
                "      el.dispatchEvent(new Event('input', { bubbles: true }));" +
                "      el.dispatchEvent(new Event('change', { bubbles: true }));" +
                "      return 'contenteditable';" +
                "    }" +
                "  }" +
                "  " +
                "  // Try textareas" +
                "  const textareas = document.querySelectorAll('textarea');" +
                "  for (const ta of textareas) {" +
                "    if (ta.offsetParent !== null && !ta.disabled && !ta.readOnly) {" +
                "      console.log('CxFix: Found textarea', ta);" +
                "      ta.focus();" +
                "      ta.value = PROMPT;" +
                "      ta.dispatchEvent(new Event('input', { bubbles: true }));" +
                "      ta.dispatchEvent(new Event('change', { bubbles: true }));" +
                "      return 'textarea';" +
                "    }" +
                "  }" +
                "  " +
                "  // Try text inputs" +
                "  const inputs = document.querySelectorAll('input[type=\"text\"], input:not([type])');" +
                "  for (const inp of inputs) {" +
                "    if (inp.offsetParent !== null && !inp.disabled && !inp.readOnly) {" +
                "      console.log('CxFix: Found input', inp);" +
                "      inp.focus();" +
                "      inp.value = PROMPT;" +
                "      inp.dispatchEvent(new Event('input', { bubbles: true }));" +
                "      inp.dispatchEvent(new Event('change', { bubbles: true }));" +
                "      return 'input';" +
                "    }" +
                "  }" +
                "  " +
                "  console.log('CxFix: No input field found');" +
                "  return null;" +
                "})();";

            // Execute JavaScript using the CefBrowser interface directly
            // This avoids JPMS module access issues with RemoteBrowser
            CefFrame mainFrame = cefBrowser.getMainFrame();
            if (mainFrame != null) {
                mainFrame.executeJavaScript(setPromptJs, "", 0);
                LOGGER.info("CxFix: Executed JavaScript via mainFrame to set prompt in Augment");
            } else {
                // Fallback: try direct execution on browser (may work in some versions)
                cefBrowser.executeJavaScript(setPromptJs, "", 0);
                LOGGER.info("CxFix: Executed JavaScript directly to set prompt in Augment");
            }

            // Small delay before sending
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // JavaScript to trigger send - try multiple approaches
            String sendJs = 
                "(() => {" +
                "  console.log('CxFix: Looking for send button');" +
                "  " +
                "  // Look for send button by various attributes" +
                "  const selectors = [" +
                "    'button[type=\"submit\"]'," +
                "    'button[aria-label*=\"send\" i]'," +
                "    'button[aria-label*=\"submit\" i]'," +
                "    'button[title*=\"send\" i]'," +
                "    'button[title*=\"submit\" i]'," +
                "    '[role=\"button\"][aria-label*=\"send\" i]'," +
                "    '.send-button'," +
                "    '[class*=\"send\"]'," +
                "    '[class*=\"submit\"]'" +
                "  ];" +
                "  " +
                "  for (const selector of selectors) {" +
                "    const btn = document.querySelector(selector);" +
                "    if (btn && btn.offsetParent !== null && !btn.disabled) {" +
                "      console.log('CxFix: Found send button', btn);" +
                "      btn.click();" +
                "      return 'clicked';" +
                "    }" +
                "  }" +
                "  " +
                "  // Try pressing Enter on the focused element" +
                "  console.log('CxFix: No send button found, trying Enter key');" +
                "  const active = document.activeElement;" +
                "  if (active) {" +
                "    const enterEvent = new KeyboardEvent('keydown', {" +
                "      key: 'Enter'," +
                "      code: 'Enter'," +
                "      keyCode: 13," +
                "      which: 13," +
                "      bubbles: true," +
                "      cancelable: true" +
                "    });" +
                "    active.dispatchEvent(enterEvent);" +
                "    return 'enter';" +
                "  }" +
                "  " +
                "  return null;" +
                "})();";

            // Execute send JavaScript using CefFrame
            if (mainFrame != null) {
                mainFrame.executeJavaScript(sendJs, "", 0);
            } else {
                cefBrowser.executeJavaScript(sendJs, "", 0);
            }
            LOGGER.info("CxFix: Executed JavaScript to send message in Augment");

            return true;
        } catch (Exception e) {
            LOGGER.warn("CxFix: JavaScript execution failed: " + e.getMessage());
            if (e.getCause() != null) {
                LOGGER.warn("CxFix: Caused by: " + e.getCause().getMessage());
            }
            // Log the full stack trace at debug level
            LOGGER.debug("CxFix: Full exception details", e);
            return false;
        }
    }

    /**
     * Gets the CefBrowser interface from a JBCefBrowser.
     * Uses the public interface to avoid JPMS module access issues.
     */
    private static @Nullable CefBrowser getCefBrowserInterface(@NotNull Object jbCefBrowser) {
        try {
            // Try getCefBrowser() method - returns CefBrowser interface
            Method getCefBrowserMethod = jbCefBrowser.getClass().getMethod("getCefBrowser");
            Object result = getCefBrowserMethod.invoke(jbCefBrowser);
            if (result instanceof CefBrowser) {
                return (CefBrowser) result;
            }
            LOGGER.debug("CxFix: getCefBrowser() returned non-CefBrowser: " + 
                        (result != null ? result.getClass().getName() : "null"));
        } catch (NoSuchMethodException e) {
            LOGGER.debug("CxFix: getCefBrowser() not found, trying alternative methods");
        } catch (Exception e) {
            LOGGER.debug("CxFix: getCefBrowser() failed: " + e.getMessage());
        }

        // Try to find CefBrowser via reflection on fields
        try {
            Class<?> clazz = jbCefBrowser.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    // Look for fields that might hold a CefBrowser
                    if (CefBrowser.class.isAssignableFrom(field.getType()) ||
                        field.getType().getName().contains("CefBrowser")) {
                        field.setAccessible(true);
                        Object value = field.get(jbCefBrowser);
                        if (value instanceof CefBrowser) {
                            return (CefBrowser) value;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            LOGGER.debug("CxFix: Field search for CefBrowser failed: " + e.getMessage());
        }

        return null;
    }

    /**
     * Escapes a string for safe use in JavaScript (using JSON-like escaping).
     */
    private static String escapeForJavaScript(@NotNull String s) {
        // Use a more robust escaping approach
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < ' ' || c > '~') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ==================== Tool Window Helpers ====================

    /**
     * Attempts to open Augment chat.
     */
    private static boolean tryOpenAugmentChat(@NotNull Project project) {
        if (tryOpenAugmentToolWindow(project)) {
            return true;
        }
        return tryInvokeAugmentAction(project);
    }

    /**
     * Finds the Augment tool window.
     */
    private static @Nullable ToolWindow findAugmentToolWindow(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        for (String toolWindowId : AUGMENT_TOOL_WINDOW_IDS) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
            if (toolWindow != null) {
                return toolWindow;
            }
        }
        return null;
    }

    /**
     * Opens Augment chat via tool window.
     */
    private static boolean tryOpenAugmentToolWindow(@NotNull Project project) {
        ToolWindow toolWindow = findAugmentToolWindow(project);
        if (toolWindow != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                toolWindow.show(() -> {
                    toolWindow.activate(() -> {
                        LOGGER.debug("CxFix: Augment tool window activated");
                    });
                });
            });
            return true;
        }
        return false;
    }

    /**
     * Invokes Augment chat action.
     */
    private static boolean tryInvokeAugmentAction(@NotNull Project project) {
        ActionManager actionManager = ActionManager.getInstance();

        for (String actionId : AUGMENT_CHAT_ACTION_IDS) {
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
                            "CxOneAssist.FixWithAugment", null, dataContext);
                    ActionUtil.performActionDumbAwareWithCallbacks(action, event);
                });
                LOGGER.debug("CxFix: Invoked Augment action: " + actionId);
                return true;
            }
        }
        return false;
    }

    // ==================== Debugging Helpers ====================

    /**
     * Logs all UI components for debugging.
     */
    private static void logAllComponents(@NotNull ToolWindow toolWindow) {
        // Always log component hierarchy for Augment (helps debugging webview issues)
        LOGGER.info("CxFix: === Augment component hierarchy dump ===");
        Content[] contents = toolWindow.getContentManager().getContents();
        for (int i = 0; i < contents.length; i++) {
            Content content = contents[i];
            JComponent component = content.getComponent();
            if (component != null) {
                LOGGER.info("CxFix: Content[" + i + "] displayName: " + content.getDisplayName());
                logComponentHierarchy(component, 0, new StringBuilder());
            }
        }
        LOGGER.info("CxFix: === End Augment component hierarchy dump ===");
    }

    /**
     * Recursively logs component hierarchy, highlighting JCEF components.
     */
    private static void logComponentHierarchy(@NotNull Component component, int depth, @NotNull StringBuilder foundJcef) {
        if (depth > 15) return; // Limit depth
        
        String className = component.getClass().getName();
        String classNameLower = className.toLowerCase();
        
        // Check if this is a JCEF/browser component (important to log)
        boolean isJcefRelated = classNameLower.contains("jcef") || 
                               classNameLower.contains("cefbrowser") || 
                               classNameLower.contains("webview") ||
                               classNameLower.contains("browser");
        
        if (isJcefRelated) {
            String componentInfo = String.format("JCEF FOUND at depth %d: %s [name=%s]",
                    depth, className, component.getName());
            LOGGER.info("CxFix: *** " + componentInfo + " ***");
            foundJcef.append(className).append("; ");
        } else if (depth <= 3) {
            // Log top-level components to understand structure
            LOGGER.info("CxFix: [depth=" + depth + "] " + className);
        }

        // Also log via reflection if there are browser-related fields
        if (!isJcefRelated) {
            try {
                for (java.lang.reflect.Field field : component.getClass().getDeclaredFields()) {
                    String fieldTypeName = field.getType().getName().toLowerCase();
                    if (fieldTypeName.contains("jcef") || fieldTypeName.contains("cefbrowser")) {
                        LOGGER.info("CxFix: *** JCEF FIELD found in " + className + 
                                   ": " + field.getName() + " of type " + field.getType().getName() + " ***");
                        foundJcef.append("field:").append(field.getName()).append("; ");
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }
        }

        if (component instanceof Container) {
            Container container = (Container) component;
            for (Component child : container.getComponents()) {
                logComponentHierarchy(child, depth + 1, foundJcef);
            }
        }
    }

    // ==================== Clipboard Helpers ====================

    /**
     * Copies text to the system clipboard.
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
