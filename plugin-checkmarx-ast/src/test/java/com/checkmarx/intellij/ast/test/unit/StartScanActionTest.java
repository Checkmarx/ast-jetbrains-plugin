package com.checkmarx.intellij.ast.test.unit;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.commands.Scan;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.actions.StartScanAction;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StartScanActionTest {

    @Mock
    private Project mockProject;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    @Mock
    private com.checkmarx.intellij.tool.window.actions.selection.RootGroup mockRootGroup;


    @Mock
    private com.checkmarx.intellij.tool.window.actions.selection.BranchSelectionGroup mockBranchSelectionGroup;


    @Mock
    private CxToolWindowPanel mockCxToolWindowPanel;

    @InjectMocks
    private StartScanAction startScanAction;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        setPrivateField(startScanAction, "workspaceProject", mockProject);
        setPrivateField(startScanAction, "propertiesComponent", mockPropertiesComponent);
        setPrivateField(startScanAction, "cxToolWindowPanel", mockCxToolWindowPanel);

        when(mockCxToolWindowPanel.getRootGroup()).thenReturn(mockRootGroup);
        when(mockRootGroup.getBranchSelectionGroup()).thenReturn(mockBranchSelectionGroup);

    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testCreateScan_whenUseLocalBranch_shouldUseActiveBranchName() throws Exception {
        try (MockedStatic<ActivityTracker> activityTrackerMockedStatic = mockStatic(ActivityTracker.class);
             MockedStatic<com.checkmarx.intellij.commands.Scan> scanMockedStatic = mockStatic(com.checkmarx.intellij.commands.Scan.class);
             MockedStatic<ProgressManager> progressManagerMockedStatic = mockStatic(ProgressManager.class);
             MockedStatic<VcsRepositoryManager> vcsRepositoryManagerMockedStatic = mockStatic(VcsRepositoryManager.class)) {


            ActivityTracker mockActivityTracker = mock(ActivityTracker.class);
            ProgressManager mockProgressManager = mock(ProgressManager.class);
            setupStaticMocks(activityTrackerMockedStatic, mockActivityTracker, progressManagerMockedStatic, mockProgressManager, vcsRepositoryManagerMockedStatic);

            mockScanCreateMethod(scanMockedStatic);
            mockBackgroundTask(mockProgressManager);
            
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn(Constants.USE_LOCAL_BRANCH);
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("testProject");
            when(mockProject.getBasePath()).thenReturn(Paths.get("path", "to", "project").toString());
            
            StartScanAction spyStartScanAction = Mockito.spy(startScanAction);

            doReturn("main").when(spyStartScanAction).getActiveBranch(mockProject);
            doNothing().when(spyStartScanAction).pollScan(anyString());

            Method createScanMethod = StartScanAction.class.getDeclaredMethod("createScan");
            createScanMethod.setAccessible(true);
            createScanMethod.invoke(spyStartScanAction);
            
            ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> projectCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> branchCaptor = ArgumentCaptor.forClass(String.class);
            scanMockedStatic.verify(() -> com.checkmarx.intellij.commands.Scan.scanCreate(pathCaptor.capture(), projectCaptor.capture(), branchCaptor.capture()));
            
            verify(mockPropertiesComponent).setValue(Constants.RUNNING_SCAN_ID_PROPERTY, "scanId");
            verify(mockActivityTracker).inc();
            assertNotNull(spyStartScanAction);
            assertEquals("main", branchCaptor.getValue());
        }
    }

    private void mockScanCreateMethod(MockedStatic<Scan> scanMockedStatic) {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getId()).thenReturn("scanId");
        scanMockedStatic.when(() ->   com.checkmarx.intellij.commands.Scan.scanCreate(any(), any(), any())).thenReturn(mockScan);
    }

    private void setupStaticMocks(MockedStatic<ActivityTracker> activityTrackerMockedStatic, ActivityTracker mockActivityTracker, MockedStatic<ProgressManager> progressManagerMockedStatic, ProgressManager mockProgressManager, MockedStatic<VcsRepositoryManager> vcsRepositoryManagerMockedStatic) {
        activityTrackerMockedStatic.when(ActivityTracker::getInstance).thenReturn(mockActivityTracker);

        progressManagerMockedStatic.when(ProgressManager::getInstance).thenReturn(mockProgressManager);

        VcsRepositoryManager mockVcsRepositoryManager = mock(VcsRepositoryManager.class);
        vcsRepositoryManagerMockedStatic.when(() -> VcsRepositoryManager.getInstance(any())).thenReturn(mockVcsRepositoryManager);
    }

    private void mockBackgroundTask(ProgressManager mockProgressManager) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Task.Backgroundable task = invocation.getArgument(0);
                task.run(mock(ProgressIndicator.class));
                task.onFinished();
                return null;
            }
        }).when(mockProgressManager).run(any(Task.Backgroundable.class));
    }
}
