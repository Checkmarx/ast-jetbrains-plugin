package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.ast.window.actions.selection.BaseSelectionGroup;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.EmptyIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseSelectionGroupTest {

    @Mock
    private Project mockProject;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private Presentation mockPresentation;

    private TestBaseSelectionGroup selectionGroup;

    @BeforeEach
    void setUp() {
        when(mockProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);

        selectionGroup = new TestBaseSelectionGroup(mockProject);
    }

    @Test
    void constructor_SetsInitialState() {
        // Assert
        assertTrue(selectionGroup.isEnabled());
        assertTrue(selectionGroup.displayTextInToolbar());
        assertFalse(selectionGroup.hideIfNoVisibleChildren());
        assertTrue(selectionGroup.isPopup());
    }

    @Test
    void update_WhenEnabledAndNoChildren_SetsEmptyIcon() {
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        selectionGroup.setEnabled(true);

        // Act
        selectionGroup.update(mockEvent);

        // Assert
        verify(mockPresentation).setDisabledIcon(EmptyIcon.ICON_16);
        verify(mockPresentation).setEnabled(true);
    }

    @Test
    void update_WhenDisabledAndNoChildren_SetsRefreshIcon() {
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);
        // Arrange
        selectionGroup.setEnabled(false);

        // Act
        selectionGroup.update(mockEvent);

        // Assert
        verify(mockPresentation).setDisabledIcon(AllIcons.Actions.Refresh);
        verify(mockPresentation).setEnabled(false);
    }

    private static class TestBaseSelectionGroup extends BaseSelectionGroup {
        TestBaseSelectionGroup(Project project) {
            super(project);
        }

        @Override
        protected String getTitle() {
            return "Test Group";
        }

        @Override
        protected void clear() {
            // Test implementation
        }

        @Override
        protected void override(Scan scan) {
            // Test implementation
        }
    }
}