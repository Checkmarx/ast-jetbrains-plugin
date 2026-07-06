package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.remediation.CopilotIntegration;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
}
