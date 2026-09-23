package com.checkmarx.intellij.devassist.test.aiagents;

import com.checkmarx.intellij.devassist.aiagents.aiassistant.AiAssistantIntegration;
import com.intellij.ide.DataManager;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiAssistantIntegration}, focused on the generation-based concurrency
 * guard that supersedes a stale "Fix with AI" automation chain when a newer one starts, and the
 * top-level availability/dispatch branches. The Timer-callback bodies
 * ({@code onPollTimerFired}, {@code onPasteTimerFired}, {@code onSendTimerFired},
 * {@code onShowToolWindowRequested}) are invoked directly via reflection rather than waiting for
 * their real {@code javax.swing.Timer} delays to elapse.
 */
@DisplayName("AiAssistantIntegration unit tests")
class AiAssistantIntegrationTest {

    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AiAssistantIntegration.class.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @SuppressWarnings("unchecked")
    private static AtomicInteger currentGenerationField() throws Exception {
        Field f = AiAssistantIntegration.class.getDeclaredField("CURRENT_GENERATION");
        f.setAccessible(true);
        return (AtomicInteger) f.get(null);
    }

    // ===== isAiAssistantAvailable =====

    @Test
    @DisplayName("isAiAssistantAvailable_PluginInstalledAndEnabled_ReturnsTrue")
    void isAiAssistantAvailable_PluginInstalledAndEnabled_ReturnsTrue() {
        IdeaPluginDescriptor plugin = mock(IdeaPluginDescriptor.class);
        when(plugin.isEnabled()).thenReturn(true);

        try (MockedStatic<PluginManagerCore> pmMock = mockStatic(PluginManagerCore.class)) {
            pmMock.when(() -> PluginManagerCore.getPlugin(any(PluginId.class))).thenReturn(plugin);

            assertTrue(AiAssistantIntegration.isAiAssistantAvailable(null));
        }
    }

    @Test
    @DisplayName("isAiAssistantAvailable_PluginNotInstalled_NoToolWindow_ReturnsFalse")
    void isAiAssistantAvailable_PluginNotInstalled_NoToolWindow_ReturnsFalse() {
        Project project = mock(Project.class);
        ToolWindowManager mockManager = mock(ToolWindowManager.class);

        try (MockedStatic<PluginManagerCore> pmMock = mockStatic(PluginManagerCore.class);
             MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class)) {
            pmMock.when(() -> PluginManagerCore.getPlugin(any(PluginId.class))).thenReturn(null);
            twMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(mockManager);
            when(mockManager.getToolWindow(anyString())).thenReturn(null);

            assertFalse(AiAssistantIntegration.isAiAssistantAvailable(project));
        }
    }

    @Test
    @DisplayName("isAiAssistantAvailable_PluginNotInstalled_ToolWindowPresent_StillReturnsFalse")
    void isAiAssistantAvailable_PluginNotInstalled_ToolWindowPresent_StillReturnsFalse() {
        Project project = mock(Project.class);
        ToolWindowManager mockManager = mock(ToolWindowManager.class);
        ToolWindow tw = mock(ToolWindow.class);

        try (MockedStatic<PluginManagerCore> pmMock = mockStatic(PluginManagerCore.class);
             MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class)) {
            pmMock.when(() -> PluginManagerCore.getPlugin(any(PluginId.class))).thenReturn(null);
            twMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(mockManager);
            when(mockManager.getToolWindow("AI Assistant")).thenReturn(tw);

            assertFalse(AiAssistantIntegration.isAiAssistantAvailable(project));
        }
    }

    // ===== openAiAssistantWithPromptDetailed top-level branches =====

    @Test
    @DisplayName("openAiAssistantWithPromptDetailed_ClipboardCopyFails_ReturnsNotAvailable")
    void openAiAssistantWithPromptDetailed_ClipboardCopyFails_ReturnsNotAvailable() {
        Project project = mock(Project.class);
        Application mockApp = mock(Application.class);
        when(mockApp.isDispatchThread()).thenReturn(true);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CopyPasteManager> cpMock = mockStatic(CopyPasteManager.class)) {
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            CopyPasteManager mockCpManager = mock(CopyPasteManager.class);
            cpMock.when(CopyPasteManager::getInstance).thenReturn(mockCpManager);
            doThrow(new RuntimeException("clipboard unavailable")).when(mockCpManager).setContents(any());

            AiAssistantIntegration.IntegrationResult result =
                    AiAssistantIntegration.openAiAssistantWithPromptDetailed("prompt", project);

            assertFalse(result.isSuccess());
        }
    }

    @Test
    @DisplayName("openAiAssistantWithPromptDetailed_AiAssistantNotAvailable_ReturnsNotAvailable")
    void openAiAssistantWithPromptDetailed_AiAssistantNotAvailable_ReturnsNotAvailable() {
        Project project = mock(Project.class);
        Application mockApp = mock(Application.class);
        when(mockApp.isDispatchThread()).thenReturn(true);

        ToolWindowManager mockManager = mock(ToolWindowManager.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CopyPasteManager> cpMock = mockStatic(CopyPasteManager.class);
             MockedStatic<PluginManagerCore> pmMock = mockStatic(PluginManagerCore.class);
             MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class)) {

            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            cpMock.when(CopyPasteManager::getInstance).thenReturn(mock(CopyPasteManager.class));
            pmMock.when(() -> PluginManagerCore.getPlugin(any(PluginId.class))).thenReturn(null);
            twMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(mockManager);
            when(mockManager.getToolWindow(anyString())).thenReturn(null);

            AiAssistantIntegration.IntegrationResult result =
                    AiAssistantIntegration.openAiAssistantWithPromptDetailed("prompt", project);

            assertFalse(result.isSuccess());
        }
    }

    @Test
    @DisplayName("openAiAssistantWithPromptDetailed_ToolWindowFound_ReturnsSuccessAndIncrementsGeneration")
    void openAiAssistantWithPromptDetailed_ToolWindowFound_ReturnsSuccessAndIncrementsGeneration() throws Exception {
        Project project = mock(Project.class);
        Application mockApp = mock(Application.class);
        when(mockApp.isDispatchThread()).thenReturn(true);
        // invokeLater is left unstubbed (default no-op) so the scheduled callback body - tested
        // separately by onShowToolWindowRequested_* below - does not also run here.

        ToolWindowManager mockManager = mock(ToolWindowManager.class);
        ToolWindow tw = mock(ToolWindow.class);
        IdeaPluginDescriptor plugin = mock(IdeaPluginDescriptor.class);
        when(plugin.isEnabled()).thenReturn(true);

        int before = currentGenerationField().get();

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CopyPasteManager> cpMock = mockStatic(CopyPasteManager.class);
             MockedStatic<PluginManagerCore> pmMock = mockStatic(PluginManagerCore.class);
             MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class)) {

            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            cpMock.when(CopyPasteManager::getInstance).thenReturn(mock(CopyPasteManager.class));
            pmMock.when(() -> PluginManagerCore.getPlugin(any(PluginId.class))).thenReturn(plugin);
            twMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(mockManager);
            when(mockManager.getToolWindow("AI Assistant")).thenReturn(tw);

            AiAssistantIntegration.IntegrationResult result =
                    AiAssistantIntegration.openAiAssistantWithPromptDetailed("prompt", project);

            assertTrue(result.isSuccess());
        }

        // Every call mints a new generation, superseding whatever automation chain preceded it.
        assertEquals(before + 1, currentGenerationField().get());
    }

    // ===== Generation guard: stale invocations of the extracted Timer-callback bodies must no-op =====

    @Test
    @DisplayName("onPasteTimerFired_WhenGenerationStale_DoesNotAttemptPaste")
    void onPasteTimerFired_WhenGenerationStale_DoesNotAttemptPaste() throws Exception {
        currentGenerationField().set(5);
        int staleGeneration = 4;

        try (MockedStatic<DataManager> dmMock = mockStatic(DataManager.class)) {
            invokeStatic("onPasteTimerFired",
                    new Class[]{Project.class, String.class, int.class},
                    mock(Project.class), "prompt", staleGeneration);

            // A stale generation must bail out before ever asking for the focused component's
            // DataContext - the first thing a real paste attempt needs.
            dmMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("onSendTimerFired_WhenGenerationStale_DoesNotAttemptSend")
    void onSendTimerFired_WhenGenerationStale_DoesNotAttemptSend() throws Exception {
        currentGenerationField().set(5);
        int staleGeneration = 4;

        try (MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {
            DataContext dataContext = dataId -> null;
            invokeStatic("onSendTimerFired",
                    new Class[]{DataContext.class, int.class},
                    dataContext, staleGeneration);

            amMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("startNewChatAndPaste_WhenGenerationStale_DoesNotInvokeNewChatAction")
    void startNewChatAndPaste_WhenGenerationStale_DoesNotInvokeNewChatAction() throws Exception {
        currentGenerationField().set(5);
        int staleGeneration = 4;

        try (MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {
            invokeStatic("startNewChatAndPaste",
                    new Class[]{Project.class, String.class, int.class},
                    mock(Project.class), "prompt", staleGeneration);

            amMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("onShowToolWindowRequested_WhenGenerationStale_DoesNotShowOrActivate")
    void onShowToolWindowRequested_WhenGenerationStale_DoesNotShowOrActivate() throws Exception {
        currentGenerationField().set(5);
        int staleGeneration = 4;

        ToolWindow tw = mock(ToolWindow.class);

        invokeStatic("onShowToolWindowRequested",
                new Class[]{ToolWindow.class, Project.class, String.class, int.class},
                tw, mock(Project.class), "prompt", staleGeneration);

        verify(tw, never()).show(any());
        verify(tw, never()).activate(any(Runnable.class));
    }

    @Test
    @DisplayName("onPollTimerFired_WhenGenerationStale_DoesNotPollAgain")
    void onPollTimerFired_WhenGenerationStale_DoesNotPollAgain() throws Exception {
        currentGenerationField().set(5);
        int staleGeneration = 4;

        try (MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class)) {
            invokeStatic("onPollTimerFired",
                    new Class[]{Project.class, String.class, int.class, int.class},
                    mock(Project.class), "prompt", staleGeneration, 10);

            // A stale generation must bail out before even looking up the tool window again.
            twMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("onPollTimerFired_WhenAttemptsExhausted_FiresBlindFallbackWithoutThrowing")
    void onPollTimerFired_WhenAttemptsExhausted_FiresBlindFallbackWithoutThrowing() throws Exception {
        Project project = mock(Project.class);
        ToolWindowManager mockManager = mock(ToolWindowManager.class);
        int generation = currentGenerationField().incrementAndGet();

        try (MockedStatic<ToolWindowManager> twMock = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {

            twMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(mockManager);
            when(mockManager.getToolWindow(anyString())).thenReturn(null); // never appears
            amMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));

            // attemptsRemaining == 1 means this is the last attempt - the blind fallback branch
            // (fire the new-chat action directly, then schedule a paste) must run without
            // throwing even though nothing in this test provides a live IDE.
            assertDoesNotThrow(() -> invokeStatic("onPollTimerFired",
                    new Class[]{Project.class, String.class, int.class, int.class},
                    project, "prompt", generation, 1));
        }
    }
}
