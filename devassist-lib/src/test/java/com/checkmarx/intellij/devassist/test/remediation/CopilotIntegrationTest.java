package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.remediation.CopilotIntegration;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CopilotIntegrationTest {

    static MockedStatic<ApplicationManager> appManagerMock;
    static MockedStatic<CopyPasteManager> copyPasteManagerMock;
    static Application mockApp;
    static CopyPasteManager mockCpm;

    @BeforeAll
    static void setupStaticMocks() {
        mockApp = mock(Application.class, RETURNS_DEEP_STUBS);
        appManagerMock = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS);
        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

        mockCpm = mock(CopyPasteManager.class);
        copyPasteManagerMock = mockStatic(CopyPasteManager.class, CALLS_REAL_METHODS);
        copyPasteManagerMock.when(CopyPasteManager::getInstance).thenReturn(mockCpm);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (appManagerMock != null) appManagerMock.close();
        if (copyPasteManagerMock != null) copyPasteManagerMock.close();
    }

    @BeforeEach
    void resetCpmStub() {
        // Reset any per-test stubs on the shared mock so earlier tests don't pollute later ones
        doNothing().when(mockCpm).setContents(any());
    }

    @Test
    void testIsCopilotAvailable_nullProject_noActions_returnsFalse() {
        try (MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {
            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            assertFalse(CopilotIntegration.isCopilotAvailable(null));
        }
    }

    @Test
    void testIsCopilotAvailable_nullProject_withMatchingAction_returnsTrue() {
        try (MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {
            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            AnAction mockAction = mock(AnAction.class);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(mockAction);

            assertTrue(CopilotIntegration.isCopilotAvailable(null));
        }
    }

    @Test
    void testIsCopilotAvailable_withProject_toolWindowFound_returnsTrue() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class)) {
            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            ToolWindow mockToolWindow = mock(ToolWindow.class);
            when(mockTwm.getToolWindow("GitHub Copilot Chat")).thenReturn(mockToolWindow);

            assertTrue(CopilotIntegration.isCopilotAvailable(mockProject));
        }
    }

    @Test
    void testIsCopilotAvailable_withProject_noToolWindowNoAction_returnsFalse() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            assertFalse(CopilotIntegration.isCopilotAvailable(mockProject));
        }
    }

    @Test
    void testOpenCopilotWithPromptDetailed_copilotNotAvailable_returnsNotAvailableResult() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, null);

            assertEquals(CopilotIntegration.OperationResult.COPILOT_NOT_AVAILABLE, result.getResult());
            assertFalse(result.isSuccess());
            assertNotNull(result.getMessage());
            assertNull(result.getException());
        }
    }

    @Test
    void testOpenCopilotWithPrompt_returnsFalse_whenCopilotNotAvailable() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            assertFalse(CopilotIntegration.openCopilotWithPrompt("fix this", mockProject));
        }
    }

    @Test
    void testOpenCopilotWithPromptDetailed_copilotAvailable_returnsPartialSuccess() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            AnAction mockAction = mock(AnAction.class);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(mockAction);

            doAnswer(inv -> inv.getArgument(0, Computable.class).compute())
                    .when(mockApp).runReadAction(any(Computable.class));
            doNothing().when(mockApp).invokeLater(any(Runnable.class));

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, null);

            assertTrue(result.isSuccess());
            assertEquals(CopilotIntegration.OperationResult.PARTIAL_SUCCESS, result.getResult());
        }
    }

    @Test
    void testIntegrationResult_isSuccess_copilotNotAvailable_returnsFalse() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("prompt", mockProject, null);

            assertFalse(result.isSuccess());
        }
    }

    @Test
    void openCopilotWithPromptDetailed_copilotAvailable_butChatOpenFails_returnsCopilotNotAvailable() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            AnAction mockAction = mock(AnAction.class);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(mockAction);

            // runReadAction returns false — Copilot available but chat window could not be opened
            doReturn(false).when(mockApp).runReadAction(any(Computable.class));

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, null);

            assertEquals(CopilotIntegration.OperationResult.COPILOT_NOT_AVAILABLE, result.getResult());
            assertFalse(result.isSuccess());
            assertNotNull(result.getMessage());
            assertNull(result.getException());
        }
    }

    @Test
    void openCopilotWithPromptDetailed_withCallback_callbackInvokedWhenCopilotUnavailable() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            // Execute invokeLater lambda so the callback fires
            doAnswer(inv -> {
                inv.getArgument(0, Runnable.class).run();
                return null;
            }).when(mockApp).invokeLater(any(Runnable.class));

            List<CopilotIntegration.IntegrationResult> captured = new ArrayList<>();
            Consumer<CopilotIntegration.IntegrationResult> callback = captured::add;

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, callback);

            assertEquals(CopilotIntegration.OperationResult.COPILOT_NOT_AVAILABLE, result.getResult());
            assertFalse(captured.isEmpty());
            assertEquals(CopilotIntegration.OperationResult.COPILOT_NOT_AVAILABLE,
                    captured.get(0).getResult());
        }
    }

    @Test
    void isCopilotAvailable_nullProject_noMatchingActions_returnsFalse() {
        try (MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {
            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            // None of the known action IDs match
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            assertFalse(CopilotIntegration.isCopilotAvailable(null));
        }
    }

    @Test
    void isCopilotAvailable_nullProject_secondActionIdMatches_returnsTrue() {
        try (MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {
            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            // First action returns null, second returns a mock action
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(null);
            when(mockActionManager.getAction("GitHub.Copilot.Chat.Show")).thenReturn(mock(AnAction.class));

            assertTrue(CopilotIntegration.isCopilotAvailable(null));
        }
    }

    @Test
    void openCopilotWithPrompt_delegatesToDetailed_returnsTrueWhenPartialSuccess() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            AnAction mockAction = mock(AnAction.class);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(mockAction);

            doAnswer(inv -> inv.getArgument(0, Computable.class).compute())
                    .when(mockApp).runReadAction(any(Computable.class));
            doNothing().when(mockApp).invokeLater(any(Runnable.class));

            boolean result = CopilotIntegration.openCopilotWithPrompt("fix", mockProject);
            assertTrue(result);
        }
    }

    @Test
    void openCopilotWithPromptDetailed_clipboardFails_returnsFailedResult() {
        // Make invokeAndWait execute the lambda so CopyPasteManager.setContents() is actually called
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(mockApp).invokeAndWait(any(Runnable.class));
        doThrow(new RuntimeException("clipboard unavailable")).when(mockCpm).setContents(any());

        Project mockProject = mock(Project.class);
        CopilotIntegration.IntegrationResult result =
                CopilotIntegration.openCopilotWithPromptDetailed("prompt", mockProject, null);

        assertEquals(CopilotIntegration.OperationResult.FAILED, result.getResult());
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
        assertNull(result.getException());

        // Restore for subsequent tests
        doNothing().when(mockCpm).setContents(any());
        doNothing().when(mockApp).invokeAndWait(any(Runnable.class));
    }

    @Test
    void openCopilotWithPromptDetailed_clipboardFails_callbackReceivesFailed() {
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(mockApp).invokeAndWait(any(Runnable.class));
        doThrow(new RuntimeException("clipboard unavailable")).when(mockCpm).setContents(any());

        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(mockApp).invokeLater(any(Runnable.class));

        Project mockProject = mock(Project.class);
        List<CopilotIntegration.IntegrationResult> captured = new ArrayList<>();
        CopilotIntegration.openCopilotWithPromptDetailed("prompt", mockProject, captured::add);

        assertFalse(captured.isEmpty());
        assertEquals(CopilotIntegration.OperationResult.FAILED, captured.get(0).getResult());

        // Restore for subsequent tests
        doNothing().when(mockCpm).setContents(any());
        doNothing().when(mockApp).invokeAndWait(any(Runnable.class));
        doNothing().when(mockApp).invokeLater(any(Runnable.class));
    }

    @Test
    void openCopilotWithPromptDetailed_copilotAvailableViaToolWindow_returnsPartialSuccess() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            ToolWindow mockToolWindow = mock(ToolWindow.class, RETURNS_DEEP_STUBS);
            when(mockTwm.getToolWindow("GitHub Copilot Chat")).thenReturn(mockToolWindow);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction(anyString())).thenReturn(null);

            doAnswer(inv -> inv.getArgument(0, Computable.class).compute())
                    .when(mockApp).runReadAction(any(Computable.class));
            doNothing().when(mockApp).invokeLater(any(Runnable.class));

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, null);

            assertEquals(CopilotIntegration.OperationResult.PARTIAL_SUCCESS, result.getResult());
            assertTrue(result.isSuccess());
        }
    }

    @Test
    void isCopilotAvailable_nullProject_thirdActionIdMatches_returnsTrue() {
        try (MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {
            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(null);
            when(mockActionManager.getAction("GitHub.Copilot.Chat.Show")).thenReturn(null);
            when(mockActionManager.getAction("copilot.openChat")).thenReturn(mock(AnAction.class));

            assertTrue(CopilotIntegration.isCopilotAvailable(null));
        }
    }

    @Test
    void isCopilotAvailable_withProject_secondToolWindowIdMatches_returnsTrue() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class)) {
            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow("GitHub Copilot Chat")).thenReturn(null);
            when(mockTwm.getToolWindow("Copilot Chat")).thenReturn(mock(ToolWindow.class));

            assertTrue(CopilotIntegration.isCopilotAvailable(mockProject));
        }
    }

    @Test
    void isCopilotAvailable_withProject_thirdToolWindowIdMatches_returnsTrue() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class)) {
            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow("GitHub Copilot Chat")).thenReturn(null);
            when(mockTwm.getToolWindow("Copilot Chat")).thenReturn(null);
            when(mockTwm.getToolWindow("GitHub Copilot")).thenReturn(mock(ToolWindow.class));

            assertTrue(CopilotIntegration.isCopilotAvailable(mockProject));
        }
    }

    @Test
    void integrationResult_fullSuccess_isSuccess_returnsTrue() throws Exception {
        Method fullSuccessMethod = CopilotIntegration.IntegrationResult.class
                .getDeclaredMethod("fullSuccess", String.class);
        fullSuccessMethod.setAccessible(true);
        CopilotIntegration.IntegrationResult result =
                (CopilotIntegration.IntegrationResult) fullSuccessMethod.invoke(null, "done");

        assertTrue(result.isSuccess());
        assertEquals(CopilotIntegration.OperationResult.FULL_SUCCESS, result.getResult());
        assertNotNull(result.getMessage());
        assertNull(result.getException());
    }

    @Test
    void integrationResult_partialSuccess_isSuccessTrue_andGettersWork() {
        try (MockedStatic<ToolWindowManager> twmStatic = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> actionManagerStatic = mockStatic(ActionManager.class)) {

            Project mockProject = mock(Project.class);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmStatic.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(anyString())).thenReturn(null);

            ActionManager mockActionManager = mock(ActionManager.class);
            actionManagerStatic.when(ActionManager::getInstance).thenReturn(mockActionManager);
            AnAction mockAction = mock(AnAction.class);
            when(mockActionManager.getAction("copilot.chat.show")).thenReturn(mockAction);

            doAnswer(inv -> inv.getArgument(0, Computable.class).compute())
                    .when(mockApp).runReadAction(any(Computable.class));
            doNothing().when(mockApp).invokeLater(any(Runnable.class));

            CopilotIntegration.IntegrationResult result =
                    CopilotIntegration.openCopilotWithPromptDetailed("fix this", mockProject, null);

            assertTrue(result.isSuccess());
            assertNotNull(result.getResult());
            assertNotNull(result.getMessage());
            assertFalse(result.getMessage().isEmpty());
            assertNull(result.getException());
        }
    }

    // ==================== isSendAction ====================

    /**
     * Stand-in for Copilot's real {@code com.github.copilot.chat.input.SendMessageAction} -
     * same simple class name, used to verify the action-class-name matching rule
     * without depending on Copilot's actual (unavailable at compile time) class.
     */
    private static class SendMessageAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // no-op test double
        }
    }

    private static class UnrelatedAction extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // no-op test double
        }
    }

    private static Method isSendActionMethod() throws NoSuchMethodException {
        Method m = CopilotIntegration.class.getDeclaredMethod("isSendAction", ActionButton.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    void isSendAction_actionClassNameContainsSend_returnsTrue() throws Exception {
        ActionButton mockButton = mock(ActionButton.class);
        when(mockButton.getAction()).thenReturn(new SendMessageAction());

        boolean result = (boolean) isSendActionMethod().invoke(null, mockButton);

        assertTrue(result);
    }

    @Test
    void isSendAction_unrelatedActionButSendTooltip_returnsTrue() throws Exception {
        ActionButton mockButton = mock(ActionButton.class);
        when(mockButton.getAction()).thenReturn(new UnrelatedAction());
        when(mockButton.getToolTipText()).thenReturn("Send message (Enter)");

        boolean result = (boolean) isSendActionMethod().invoke(null, mockButton);

        assertTrue(result);
    }

    @Test
    void isSendAction_unrelatedActionAndTooltip_returnsFalse() throws Exception {
        ActionButton mockButton = mock(ActionButton.class);
        when(mockButton.getAction()).thenReturn(new UnrelatedAction());
        when(mockButton.getToolTipText()).thenReturn("Configure agents...");

        boolean result = (boolean) isSendActionMethod().invoke(null, mockButton);

        assertFalse(result);
    }

    @Test
    void isSendAction_nullTooltip_returnsFalse() throws Exception {
        ActionButton mockButton = mock(ActionButton.class);
        when(mockButton.getAction()).thenReturn(new UnrelatedAction());
        when(mockButton.getToolTipText()).thenReturn(null);

        boolean result = (boolean) isSendActionMethod().invoke(null, mockButton);

        assertFalse(result);
    }

    @Test
    void isSendAction_nullAction_returnsFalse() throws Exception {
        ActionButton mockButton = mock(ActionButton.class);
        when(mockButton.getAction()).thenReturn(null);

        boolean result = (boolean) isSendActionMethod().invoke(null, mockButton);

        assertFalse(result);
    }

    // ==================== pollUntilTrue / pollForResult ====================

    private static Method pollUntilTrueMethod() throws NoSuchMethodException {
        Method m = CopilotIntegration.class.getDeclaredMethod("pollUntilTrue", int.class, BooleanSupplier.class);
        m.setAccessible(true);
        return m;
    }

    private static Method pollForResultMethod() throws NoSuchMethodException {
        Method m = CopilotIntegration.class.getDeclaredMethod("pollForResult", int.class, Supplier.class);
        m.setAccessible(true);
        return m;
    }

    /** Makes the shared {@code mockApp.invokeAndWait(Runnable)} actually run the runnable. */
    private static void runInvokeAndWaitSynchronously() {
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(mockApp).invokeAndWait(any(Runnable.class));
    }

    private static void restoreInvokeAndWait() {
        doNothing().when(mockApp).invokeAndWait(any(Runnable.class));
    }

    @Test
    void pollUntilTrue_succeedsOnFirstAttempt_returnsTrueWithoutRetrying() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        BooleanSupplier attempt = () -> {
            calls.incrementAndGet();
            return true;
        };

        boolean result = (boolean) pollUntilTrueMethod().invoke(null, 1000, attempt);

        assertTrue(result);
        assertEquals(1, calls.get());
        restoreInvokeAndWait();
    }

    @Test
    void pollUntilTrue_succeedsAfterRetries_withinBudget_returnsTrue() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        // Fails the first 2 attempts, succeeds on the 3rd
        BooleanSupplier attempt = () -> calls.incrementAndGet() >= 3;

        boolean result = (boolean) pollUntilTrueMethod().invoke(null, 2000, attempt);

        assertTrue(result);
        assertEquals(3, calls.get());
        restoreInvokeAndWait();
    }

    @Test
    void pollUntilTrue_neverSucceeds_exhaustsBudget_returnsFalse() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        BooleanSupplier attempt = () -> {
            calls.incrementAndGet();
            return false;
        };

        // A tiny budget means only the unconditional first attempt fits before the
        // poll interval sleep pushes past the deadline.
        boolean result = (boolean) pollUntilTrueMethod().invoke(null, 1, attempt);

        assertFalse(result);
        assertEquals(1, calls.get());
        restoreInvokeAndWait();
    }

    @Test
    void pollForResult_returnsNonNullImmediately_withoutRetrying() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        Supplier<String> attempt = () -> {
            calls.incrementAndGet();
            return "found";
        };

        Object result = pollForResultMethod().invoke(null, 1000, attempt);

        assertEquals("found", result);
        assertEquals(1, calls.get());
        restoreInvokeAndWait();
    }

    @Test
    void pollForResult_eventuallyFindsResult_afterRetries() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        Supplier<String> attempt = () -> calls.incrementAndGet() >= 3 ? "ready" : null;

        Object result = pollForResultMethod().invoke(null, 2000, attempt);

        assertEquals("ready", result);
        assertEquals(3, calls.get());
        restoreInvokeAndWait();
    }

    @Test
    void pollForResult_neverFound_exhaustsBudget_returnsNull() throws Exception {
        runInvokeAndWaitSynchronously();
        AtomicInteger calls = new AtomicInteger(0);
        Supplier<String> attempt = () -> {
            calls.incrementAndGet();
            return null;
        };

        Object result = pollForResultMethod().invoke(null, 1, attempt);

        assertNull(result);
        assertEquals(1, calls.get());
        restoreInvokeAndWait();
    }
}
