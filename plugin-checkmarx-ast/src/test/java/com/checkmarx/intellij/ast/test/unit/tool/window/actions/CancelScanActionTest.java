package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.ast.commands.Scan;
import com.checkmarx.intellij.ast.window.actions.CancelScanAction;
import com.checkmarx.intellij.ast.window.actions.StartScanAction;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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

    @Test
    void update_WhenProjectIsNull_SetsEnabledTrueViaExceptionPath() {
        // When getProject() returns null, Objects.requireNonNull throws → exception path sets enabled=true
        when(mockEvent.getProject()).thenReturn(null);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class)) {
            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);

            cancelScanAction.update(mockEvent);

            verify(mockPresentation).setEnabled(true);
        }
    }

    @Test
    void update_WhenScanIdNotBlank_EnablesAction() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockEvent.getPresentation()).thenReturn(mockPresentation);

        try (MockedStatic<StartScanAction> startScanActionMockedStatic = mockStatic(StartScanAction.class);
             MockedStatic<PropertiesComponent> propertiesComponentMockedStatic = mockStatic(PropertiesComponent.class)) {

            startScanActionMockedStatic.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);
            propertiesComponentMockedStatic.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            when(mockPropertiesComponent.getValue(com.checkmarx.intellij.common.utils.Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("non-empty-scan-id");

            cancelScanAction.update(mockEvent);

            verify(mockPresentation).setEnabled(true);
        }
    }

    @Test
    void actionPerformed_SubmitsBackgroundTask() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        ProgressManager mockProgressManager = mock(ProgressManager.class);

        try (MockedStatic<PropertiesComponent> pcMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ProgressManager> pmMock = mockStatic(ProgressManager.class)) {

            pcMock.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            pmMock.when(ProgressManager::getInstance).thenReturn(mockProgressManager);

            cancelScanAction.actionPerformed(mockEvent);

            verify(mockProgressManager).run(any(Task.Backgroundable.class));
        }
    }

    @Test
    void actionPerformed_TaskRun_CancelsScanAndClearsProperty() {
        when(mockEvent.getProject()).thenReturn(mockProject);
        when(mockPropertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("scan-123");

        ProgressManager mockProgressManager = mock(ProgressManager.class);
        doAnswer(invocation -> {
            Task.Backgroundable task = invocation.getArgument(0);
            task.run(mock(ProgressIndicator.class));
            return null;
        }).when(mockProgressManager).run(any(Task.Backgroundable.class));

        ActivityTracker mockActivityTracker = mock(ActivityTracker.class);

        try (MockedStatic<PropertiesComponent> pcMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ProgressManager> pmMock = mockStatic(ProgressManager.class);
             MockedStatic<Scan> scanMock = mockStatic(Scan.class);
             MockedStatic<ActivityTracker> actMock = mockStatic(ActivityTracker.class);
             MockedStatic<StartScanAction> saMock = mockStatic(StartScanAction.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            pcMock.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            pmMock.when(ProgressManager::getInstance).thenReturn(mockProgressManager);
            scanMock.when(() -> Scan.scanCancel(any())).thenAnswer(i -> null);
            actMock.when(ActivityTracker::getInstance).thenReturn(mockActivityTracker);
            saMock.when(StartScanAction::cancelRunningScan).thenAnswer(i -> null);

            cancelScanAction.actionPerformed(mockEvent);

            verify(mockPropertiesComponent).setValue(eq(Constants.RUNNING_SCAN_ID_PROPERTY), any());
            verify(mockActivityTracker).inc();
        }
    }
}