package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.actions.selection.ResetSelectionAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
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

        // Act
        action.actionPerformed(mockEvent);

        // Assert
        assertFalse(action.isEnabled());
        verify(mockToolWindowPanel).refreshPanel();
        verify(mockToolWindowPanel).resetPanel();
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