package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.tool.window.actions.CancelScanAction;
import com.checkmarx.intellij.tool.window.actions.StartScanAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelScanActionTest {

    @Mock
    private Project mockProject;

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    @Mock
    private Presentation mockPresentation;

    private CancelScanAction cancelScanAction;

    @BeforeEach
    void setUp() {
        cancelScanAction = new CancelScanAction();
    }

    @Test
    void update_WhenUserHasPermissionsAndScanRunning_EnablesAndShowsAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class);
             MockedStatic<PropertiesComponent> propertiesComponentMockedStatic = mockStatic(PropertiesComponent.class)) {
            
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);
            propertiesComponentMockedStatic.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            when(mockPropertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("scan-123");

            // Act
            cancelScanAction.update(mockEvent);

            // Assert
            verify(mockPresentation).setVisible(true);
            verify(mockPresentation).setEnabled(true);
        }
    }

    @Test
    void update_WhenUserHasNoPermissions_HidesAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class);
             MockedStatic<PropertiesComponent> propertiesComponentMockedStatic = mockStatic(PropertiesComponent.class)) {
            
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(false);
            propertiesComponentMockedStatic.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);

            // Act
            cancelScanAction.update(mockEvent);

            // Assert
            verify(mockPresentation).setVisible(false);
        }
    }

    @Test
    void update_WhenNoScanRunning_DisablesAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        // Arrange
        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class);
             MockedStatic<PropertiesComponent> propertiesComponentMockedStatic = mockStatic(PropertiesComponent.class)) {
            
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);
            propertiesComponentMockedStatic.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            when(mockPropertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("");

            // Act
            cancelScanAction.update(mockEvent);

            // Assert
            verify(mockPresentation).setEnabled(false);
        }
    }

    @Test
    void getActionUpdateThread_ReturnsBGT() {
        // Act
        ActionUpdateThread result = cancelScanAction.getActionUpdateThread();

        // Assert
        assertEquals(ActionUpdateThread.BGT, result);
    }
} 