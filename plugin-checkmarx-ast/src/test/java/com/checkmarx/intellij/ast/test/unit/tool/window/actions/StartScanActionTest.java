package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.commands.TenantSetting;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartScanActionTest {

    @Mock
    private Project mockProject;

    @Mock
    private AnActionEvent mockEvent;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    @Mock
    private Presentation mockPresentation;

    private StartScanAction startScanAction;

    @BeforeEach
    void setUp() {
        startScanAction = new StartScanAction();
    }

    @Test
    void getUserHasPermissionsToScan_WhenPermissionsAllowed_ReturnsTrue() {
        // Arrange
        try (MockedStatic<TenantSetting> tenantSettingMockedStatic = mockStatic(TenantSetting.class)) {
            tenantSettingMockedStatic.when(TenantSetting::isScanAllowed).thenReturn(true);

            // Act
            Boolean result = StartScanAction.getUserHasPermissionsToScan();

            // Assert
            assertTrue(result);
        }
    }

    @Test
    void update_WhenUserHasPermissions_ShowsAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);
        when(mockProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);
        // Arrange
        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class)) {
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);

            // Act
            startScanAction.update(mockEvent);

            // Assert
            verify(mockPresentation).setVisible(true);
        }
    }

    @Test
    void update_WhenUserHasNoPermissions_HidesAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);
        when(mockProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);
        // Arrange
        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class)) {
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(false);

            // Act
            startScanAction.update(mockEvent);

            // Assert
            verify(mockPresentation).setVisible(false);
        }
    }

    @Test
    void getActionUpdateThread_ReturnsBGT() {
        // Act
        ActionUpdateThread result = startScanAction.getActionUpdateThread();

        // Assert
        assertEquals(ActionUpdateThread.BGT, result);
    }
} 