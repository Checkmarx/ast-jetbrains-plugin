package com.checkmarx.intellij.ast.test.unit.tool.window.actions.group.by;

import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBy;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByBaseAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByDirectDependency;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByFileAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByPackageAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupBySeverityAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByStateAction;
import com.checkmarx.intellij.ast.window.actions.group.by.GroupByVulnerabilityTypeAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

    private Map<GroupByBaseAction, GroupBy> concreteActionMap() {
        Map<GroupByBaseAction, GroupBy> map = new LinkedHashMap<>();
        map.put(new GroupByDirectDependency(), GroupBy.DIRECT_DEPENDENCY);
        map.put(new GroupByFileAction(), GroupBy.FILE);
        map.put(new GroupByPackageAction(), GroupBy.PACKAGE);
        map.put(new GroupBySeverityAction(), GroupBy.SEVERITY);
        map.put(new GroupByStateAction(), GroupBy.STATE);
        map.put(new GroupByVulnerabilityTypeAction(), GroupBy.VULNERABILITY_TYPE_NAME);
        return map;
    }

    @Test
    void concreteActions_ConstructorsHavePresentationText() {
        for (GroupByBaseAction action : concreteActionMap().keySet()) {
            assertNotNull(action);
            assertNotNull(action.getTemplatePresentation().getText());
            assertFalse(action.getTemplatePresentation().getText().isBlank());
        }
    }

    @Test
    void concreteActions_getGroupByMatchesExpectedEnum() throws Exception {
        for (Map.Entry<GroupByBaseAction, GroupBy> entry : concreteActionMap().entrySet()) {
            GroupByBaseAction action = entry.getKey();
            Method method = action.getClass().getDeclaredMethod("getGroupBy");
            method.setAccessible(true);
            GroupBy value = (GroupBy) method.invoke(action);
            assertEquals(entry.getValue(), value,
                    "Unexpected GroupBy for " + action.getClass().getSimpleName());
        }
    }

    @Test
    void concreteActions_defaultSelectedStateMatchesDefaultGroupByList() {
        for (Map.Entry<GroupByBaseAction, GroupBy> entry : concreteActionMap().entrySet()) {
            GroupByBaseAction action = entry.getKey();
            GroupBy expected = entry.getValue();
            assertEquals(GroupBy.DEFAULT_GROUP_BY.contains(expected), action.isSelected(mockEvent),
                    "Default selected state mismatch for " + action.getClass().getSimpleName());
        }
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
