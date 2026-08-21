package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.selection.ResetSelectionAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResetSelectionActionTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private CxToolWindowPanel mockToolWindowPanel;

    @Mock
    private Presentation mockPresentation;

    private ResetSelectionAction action;

    @BeforeEach
    void setUp() {
        action = spy(new ResetSelectionAction());
    }

    @Test
    void update_WhenEnabled_EnablesPresentation() {
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        action.setEnabled(true);

        // Act
        action.update(mockEvent);

        // Assert
        verify(mockPresentation).setEnabled(true);
    }

    @Test
    void update_WhenDisabled_DisablesPresentation() {
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        action.setEnabled(false);

        // Act
        action.update(mockEvent);

        // Assert
        verify(mockPresentation).setEnabled(false);
    }

    @Test
    void actionPerformed_WhenPanelExists_RefreshesAndResetsPanel() {
        // Arrange
        doReturn(mockToolWindowPanel).when(action).getCxToolWindowPanel(mockEvent);

        // Mock ActionManager to return a registered action with a presentation
        MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class);
        ActionManager mockActionManager = mock(ActionManager.class);
        com.intellij.openapi.actionSystem.AnAction registered = mock(com.intellij.openapi.actionSystem.AnAction.class);
        when(registered.getTemplatePresentation()).thenReturn(mockPresentation);
        when(mockActionManager.getAction("Checkmarx.ResetSelection")).thenReturn(registered);
        amMock.when(ActionManager::getInstance).thenReturn(mockActionManager);

        try {
            // Act
            action.actionPerformed(mockEvent);

            // Assert: registered presentation should be disabled and panel methods called
            verify(mockPresentation).setEnabled(false);
            verify(mockToolWindowPanel).refreshPanel();
            verify(mockToolWindowPanel).resetPanel();
        } finally {
            amMock.close();
        }
    }

    @Test
    void actionPerformed_WhenPanelIsNull_DoesNothing() {
        // Arrange
        doReturn(null).when(action).getCxToolWindowPanel(mockEvent);

        // Act
        action.actionPerformed(mockEvent);

        // Assert
        assertTrue(action.isEnabled());
        verifyNoInteractions(mockToolWindowPanel);
    }

    @Test
    void getActionUpdateThread_ReturnsEDT() {
        // Act & Assert
        assertEquals(ActionUpdateThread.EDT, action.getActionUpdateThread());
    }
} 