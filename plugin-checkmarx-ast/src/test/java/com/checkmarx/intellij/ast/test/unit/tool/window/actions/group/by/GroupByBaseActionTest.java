package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.actions.group.by.GroupByBaseAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupByBaseActionTest {

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private CxToolWindowPanel mockToolWindowPanel;

    private TestGroupByAction testAction;

    @BeforeEach
    void setUp() {
        testAction = new TestGroupByAction(mockToolWindowPanel);
    }

    @Test
    void isSelected_ReturnsInitialState() {
        // Act & Assert
        assertFalse(testAction.isSelected(mockEvent));
    }

    @Test
    void setSelected_WhenStateIsTrue_UpdatesStateAndNotifiesPanel() {
        // Act
        testAction.setSelected(mockEvent, true);

        // Assert
        assertTrue(testAction.isSelected(mockEvent));
        verify(mockToolWindowPanel).changeGroupBy(GroupBy.FILE, true);
    }

    @Test
    void setSelected_WhenStateIsFalse_UpdatesStateAndNotifiesPanel() {
        // Arrange
        testAction.setSelected(mockEvent, true); // First set it to true
        
        // Act
        testAction.setSelected(mockEvent, false);

        // Assert
        assertFalse(testAction.isSelected(mockEvent));
        verify(mockToolWindowPanel).changeGroupBy(GroupBy.FILE, false);
    }

    @Test
    void setSelected_WhenPanelIsNull_OnlyUpdatesState() {
        // Arrange
        TestGroupByAction actionWithNullPanel = new TestGroupByAction(null);

        // Act
        actionWithNullPanel.setSelected(mockEvent, true);

        // Assert
        assertTrue(actionWithNullPanel.isSelected(mockEvent));
        verifyNoInteractions(mockToolWindowPanel);
    }

    @Test
    void getActionUpdateThread_ReturnsEDT() {
        // Act & Assert
        assertEquals(ActionUpdateThread.EDT, testAction.getActionUpdateThread());
    }

    private static class TestGroupByAction extends GroupByBaseAction {
        private final CxToolWindowPanel panel;

        TestGroupByAction(CxToolWindowPanel panel) {
            super(() -> "Test Group By");
            this.panel = panel;
        }

        @Override
        protected GroupBy getGroupBy() {
            return GroupBy.FILE;
        }

        @Override
        public CxToolWindowPanel getCxToolWindowPanel(AnActionEvent e) {
            return panel;
        }
    }
} 