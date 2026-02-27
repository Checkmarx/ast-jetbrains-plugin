package com.checkmarx.intellij.cxdevassist.test.window.actions;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.cxdevassist.window.actions.OpenCxDevAssistSettingsAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenCxDevAssistSettingsActionTest {

    private GlobalSettingsState mockGlobalState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;
    private MockedStatic<ShowSettingsUtil> mockedShowSettingsUtil;
    private AnActionEvent mockEvent;
    private Project mockProject;
    private ShowSettingsUtil mockShowSettingsUtil;

    @BeforeEach
    void setUp() {
        mockGlobalState = mock(GlobalSettingsState.class);
        mockEvent = mock(AnActionEvent.class);
        mockProject = mock(Project.class);
        mockShowSettingsUtil = mock(ShowSettingsUtil.class);

        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedShowSettingsUtil = mockStatic(ShowSettingsUtil.class);

        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
        mockedShowSettingsUtil.when(ShowSettingsUtil::getInstance).thenReturn(mockShowSettingsUtil);

        when(mockEvent.getProject()).thenReturn(mockProject);
        doNothing().when(mockShowSettingsUtil).showSettingsDialog(any(), any(Class.class));
    }
    
    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
        mockedShowSettingsUtil.close();
    }

    @Test
    @DisplayName("Action can be instantiated")
    void testActionInstantiation() {
        assertDoesNotThrow(() -> new OpenCxDevAssistSettingsAction());
    }

    @Test
    @DisplayName("Action performs without throwing")
    void testActionPerformed() {
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> action.actionPerformed(mockEvent));
    }

    @Test
    @DisplayName("Action handles null project")
    void testActionWithNullProject() {
        when(mockEvent.getProject()).thenReturn(null);
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> action.actionPerformed(mockEvent));
    }

    @Test
    @DisplayName("Update method works")
    void testUpdate() {
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> action.update(mockEvent));
    }

    @Test
    @DisplayName("Multiple action instances are independent")
    void testMultipleInstances() {
        OpenCxDevAssistSettingsAction action1 = new OpenCxDevAssistSettingsAction();
        OpenCxDevAssistSettingsAction action2 = new OpenCxDevAssistSettingsAction();
        assertNotSame(action1, action2);
    }

    @Test
    @DisplayName("Action retrieves project from event")
    void testProjectRetrieval() {
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        action.actionPerformed(mockEvent);
        verify(mockEvent, atLeastOnce()).getProject();
    }

    @Test
    @DisplayName("Global state is accessible")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Action works with valid project")
    void testWithValidProject() {
        when(mockProject.isDisposed()).thenReturn(false);
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> action.actionPerformed(mockEvent));
    }

    @Test
    @DisplayName("Action works with disposed project")
    void testWithDisposedProject() {
        when(mockProject.isDisposed()).thenReturn(true);
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> action.actionPerformed(mockEvent));
    }

    @Test
    @DisplayName("Multiple action performs work correctly")
    void testMultiplePerforms() {
        OpenCxDevAssistSettingsAction action = new OpenCxDevAssistSettingsAction();
        assertDoesNotThrow(() -> {
            action.actionPerformed(mockEvent);
            action.actionPerformed(mockEvent);
            action.actionPerformed(mockEvent);
        });
    }
}

