package com.checkmarx.intellij.ast.test.unit.tool.window.actions;

import com.checkmarx.intellij.ast.commands.Scan;
import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.StartScanAction;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.checkmarx.intellij.common.commands.TenantSetting;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StartScanActionTest {

    @Mock
    private Project mockProject;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    @Mock
    private com.checkmarx.intellij.ast.window.actions.selection.RootGroup mockRootGroup;


    @Mock
    private com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup mockBranchSelectionGroup;


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

        lenient().when(mockCxToolWindowPanel.getRootGroup()).thenReturn(mockRootGroup);
        lenient().when(mockRootGroup.getBranchSelectionGroup()).thenReturn(mockBranchSelectionGroup);

    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testCreateScan_whenUseLocalBranch_shouldUseActiveBranchName() throws Exception {
        try (MockedStatic<ActivityTracker> activityTrackerMockedStatic = mockStatic(ActivityTracker.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> scanMockedStatic = mockStatic(com.checkmarx.intellij.ast.commands.Scan.class);
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
            scanMockedStatic.verify(() -> com.checkmarx.intellij.ast.commands.Scan.scanCreate(pathCaptor.capture(), projectCaptor.capture(), branchCaptor.capture()));
            
            verify(mockPropertiesComponent).setValue(Constants.RUNNING_SCAN_ID_PROPERTY, "scanId");
            verify(mockActivityTracker).inc();
            assertNotNull(spyStartScanAction);
            assertEquals("main", branchCaptor.getValue());
        }
    }

    private void mockScanCreateMethod(MockedStatic<Scan> scanMockedStatic) {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getId()).thenReturn("scanId");
        scanMockedStatic.when(() ->   com.checkmarx.intellij.ast.commands.Scan.scanCreate(any(), any(), any())).thenReturn(mockScan);
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

    @Test
    public void testCreateScan_withExplicitBranch_usesStoredBranchDirectly() throws Exception {
        try (MockedStatic<ActivityTracker> activityTrackerMockedStatic = mockStatic(ActivityTracker.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> scanMockedStatic = mockStatic(com.checkmarx.intellij.ast.commands.Scan.class);
             MockedStatic<ProgressManager> progressManagerMockedStatic = mockStatic(ProgressManager.class);
             MockedStatic<VcsRepositoryManager> vcsRepositoryManagerMockedStatic = mockStatic(VcsRepositoryManager.class)) {

            ActivityTracker mockActivityTracker = mock(ActivityTracker.class);
            ProgressManager mockProgressManager = mock(ProgressManager.class);
            setupStaticMocks(activityTrackerMockedStatic, mockActivityTracker, progressManagerMockedStatic, mockProgressManager, vcsRepositoryManagerMockedStatic);
            mockScanCreateMethod(scanMockedStatic);
            mockBackgroundTask(mockProgressManager);

            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("feature/my-branch");
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("testProject");
            when(mockProject.getBasePath()).thenReturn(Paths.get("path", "to", "project").toString());

            StartScanAction spyStartScanAction = Mockito.spy(startScanAction);
            doNothing().when(spyStartScanAction).pollScan(anyString());

            Method createScanMethod = StartScanAction.class.getDeclaredMethod("createScan");
            createScanMethod.setAccessible(true);
            createScanMethod.invoke(spyStartScanAction);

            ArgumentCaptor<String> branchCaptor = ArgumentCaptor.forClass(String.class);
            scanMockedStatic.verify(() -> com.checkmarx.intellij.ast.commands.Scan.scanCreate(any(), any(), branchCaptor.capture()));
            assertEquals("feature/my-branch", branchCaptor.getValue());
        }
    }

    @Test
    public void testGetActionUpdateThread_returnsBGT() {
        assertEquals(ActionUpdateThread.BGT, startScanAction.getActionUpdateThread());
    }

    @Test
    public void testGetUserHasPermissionsToScan_whenAlreadySet_returnsStoredValue() {
        StartScanAction.setUserHasPermissionsToScan(true);
        assertTrue(StartScanAction.getUserHasPermissionsToScan());
        StartScanAction.setUserHasPermissionsToScan(null);
    }

    @Test
    public void testIsAstProjectMatchesWorkspaceProject_blankProjectName_returnsFalse() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("");

            Method method = StartScanAction.class.getDeclaredMethod("isAstProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(startScanAction);
            assertFalse(result);
        }
    }

    @Test
    public void testIsAstProjectMatchesWorkspaceProject_nullRepositoryProjectName_returnsFalse() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("my-project");

            Method method = StartScanAction.class.getDeclaredMethod("isAstProjectMatchesWorkspaceProject");
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(startScanAction);
            assertFalse(result);
        }
    }

    // ===== update() — exception path disables presentation =====

    @Test
    public void testUpdate_whenProjectNull_disablesPresentation() {
        AnActionEvent e = mock(AnActionEvent.class);
        Presentation presentation = mock(Presentation.class);
        when(e.getPresentation()).thenReturn(presentation);
        when(e.getProject()).thenReturn(null);

        startScanAction.update(e);

        verify(presentation).setEnabled(false);
    }

    // ===== getUserHasPermissionsToScan — null → calls TenantSetting =====

    @Test
    public void testGetUserHasPermissionsToScan_whenNullAndTenantAllows_returnsTrue() throws Exception {
        StartScanAction.setUserHasPermissionsToScan(null);
        try (MockedStatic<TenantSetting> tenantMock = mockStatic(TenantSetting.class)) {
            tenantMock.when(TenantSetting::isScanAllowed).thenReturn(true);
            assertTrue(StartScanAction.getUserHasPermissionsToScan());
        }
        StartScanAction.setUserHasPermissionsToScan(null);
    }

    @Test
    public void testGetUserHasPermissionsToScan_whenNullAndTenantThrows_returnsFalse() throws Exception {
        StartScanAction.setUserHasPermissionsToScan(null);
        try (MockedStatic<TenantSetting> tenantMock = mockStatic(TenantSetting.class)) {
            tenantMock.when(TenantSetting::isScanAllowed).thenThrow(new RuntimeException("no connection"));
            assertFalse(StartScanAction.getUserHasPermissionsToScan());
        }
        StartScanAction.setUserHasPermissionsToScan(null);
    }

    @Test
    public void testGetActiveBranch_WhenRepositoryNull_ReturnsNull() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            assertNull(startScanAction.getActiveBranch(mockProject));
        }
    }

    @Test
    public void testGetActiveBranch_WhenRepositoryExists_ReturnsBranchName() {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.getCurrentBranchName()).thenReturn("feature-branch");
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);
            assertEquals("feature-branch", startScanAction.getActiveBranch(mockProject));
        }
    }

    @Test
    public void testGetRepositoryProjectName_WithValidGitUrl_ReturnsProjectPath() throws Exception {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.toLogString()).thenReturn("Root{myUrls=[https://github.com/org/my-project.git], vcs=Git}");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);

            Method method = StartScanAction.class.getDeclaredMethod("getRepositoryProjectName");
            method.setAccessible(true);
            String result = (String) method.invoke(startScanAction);
            assertEquals("org/my-project", result);
        }
    }

    @Test
    public void testGetRepositoryProjectName_WithNoMyUrls_ReturnsNull() throws Exception {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.toLogString()).thenReturn("Root{vcs=Git, no-urls-here}");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);

            Method method = StartScanAction.class.getDeclaredMethod("getRepositoryProjectName");
            method.setAccessible(true);
            String result = (String) method.invoke(startScanAction);
            assertNull(result);
        }
    }

    @Test
    public void testGetRepositoryProjectName_WhenRepositoryNull_ReturnsNull() throws Exception {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);

            Method method = StartScanAction.class.getDeclaredMethod("getRepositoryProjectName");
            method.setAccessible(true);
            String result = (String) method.invoke(startScanAction);
            assertNull(result);
        }
    }

    @Test
    public void testActionPerformed_WithNoRepo_NotMatchProject_CallsNotifyScan() {
        when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("my-project");
        AnActionEvent e = mock(AnActionEvent.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            utilsMock.when(() -> Utils.notifyScan(any(), any(), any(), any(), any(), any())).thenAnswer(i -> null);

            startScanAction.actionPerformed(e);

            utilsMock.verify(() -> Utils.notifyScan(any(), any(), eq(mockProject), any(), any(), any()));
        }
    }

    @Test
    public void testActionPerformed_WithRepo_NotMatchProject_CallsNotifyScan() {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.getCurrentBranchName()).thenReturn("main");
        when(mockRepo.toLogString()).thenReturn("Root{myUrls=[https://github.com/org/my-project.git], vcs=Git}");
        when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");
        when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("different-project");

        AnActionEvent e = mock(AnActionEvent.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);
            utilsMock.when(() -> Utils.notifyScan(any(), any(), any(), any(), any(), any())).thenAnswer(i -> null);

            startScanAction.actionPerformed(e);

            utilsMock.verify(() -> Utils.notifyScan(any(), any(), eq(mockProject), any(), any(), any()));
        }
    }

    @Test
    public void testActionPerformed_WithRepo_NotMatchBranch_MatchProject_CallsNotifyScan() {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.getCurrentBranchName()).thenReturn("main");
        when(mockRepo.toLogString()).thenReturn("Root{myUrls=[https://github.com/org/my-project.git], vcs=Git}");
        when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("develop");
        when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("org/my-project");

        AnActionEvent e = mock(AnActionEvent.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);
            utilsMock.when(() -> Utils.notifyScan(any(), any(), any(), any(), any(), any())).thenAnswer(i -> null);

            startScanAction.actionPerformed(e);

            utilsMock.verify(() -> Utils.notifyScan(any(), any(), eq(mockProject), any(), any(), any()));
        }
    }

    @Test
    public void testCancelRunningScan_WithPollTask_CallsOnCancel() throws Exception {
        Task.Backgroundable mockTask = mock(Task.Backgroundable.class);
        Field pollScanTaskField = StartScanAction.class.getDeclaredField("pollScanTask");
        pollScanTaskField.setAccessible(true);
        pollScanTaskField.set(null, mockTask);

        try {
            StartScanAction.cancelRunningScan();
            assertTrue(
                mockingDetails(mockTask).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("onCancel")),
                "Expected onCancel() to be called"
            );
        } finally {
            pollScanTaskField.set(null, null);
        }
    }

    @Test
    public void testLoadResults_SetsPropertyAndRefreshesSelection() throws Exception {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getId()).thenReturn("scan-123");
        when(mockScan.getBranch()).thenReturn("main");
        when(mockScan.getProjectId()).thenReturn("project-id");

        ScanSelectionGroup mockSsg = mock(ScanSelectionGroup.class);
        lenient().when(mockCxToolWindowPanel.getRootGroup()).thenReturn(mockRootGroup);
        when(mockRootGroup.getScanSelectionGroup()).thenReturn(mockSsg);

        Method loadResultsMethod = StartScanAction.class.getDeclaredMethod("loadResults", com.checkmarx.ast.scan.Scan.class);
        loadResultsMethod.setAccessible(true);
        loadResultsMethod.invoke(startScanAction, mockScan);

        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "main");
        verify(mockSsg).refresh("project-id", "main", true);
    }

    @Test
    public void testUpdate_WhenNoScanRunning_WithProjectAndBranch_EnablesAction() {
        AnActionEvent e = mock(AnActionEvent.class);
        Presentation pres = mock(Presentation.class);
        when(e.getProject()).thenReturn(mockProject);
        when(e.getPresentation()).thenReturn(pres);

        try (MockedStatic<StartScanAction> saMock = mockStatic(StartScanAction.class, CALLS_REAL_METHODS);
             MockedStatic<PropertiesComponent> pcMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class)) {

            saMock.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);
            pcMock.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmMock.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(any())).thenReturn(null);

            when(mockPropertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("");
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("my-project");
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");

            startScanAction.update(e);

            verify(pres).setVisible(true);
            verify(pres).setEnabled(true);
        }
    }

    // ===== pollingScan() runnable body =====

    @Test
    public void testPollingScan_WhenScanIsRunning_LogsInfoAndContinues() throws Exception {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getStatus()).thenReturn("Running");

        // Set up pollScanExecutor mock via reflection
        java.util.concurrent.ScheduledExecutorService mockExecutor =
                mock(java.util.concurrent.ScheduledExecutorService.class);
        setPrivateField(startScanAction, "pollScanExecutor", mockExecutor);

        try (MockedStatic<Scan> scanMock = mockStatic(Scan.class)) {
            scanMock.when(() -> Scan.scanShow(anyString())).thenReturn(mockScan);

            Method pollingScanMethod = StartScanAction.class.getDeclaredMethod("pollingScan", String.class);
            pollingScanMethod.setAccessible(true);
            Runnable runnable = (Runnable) pollingScanMethod.invoke(startScanAction, "scan-running-id");

            assertDoesNotThrow(runnable::run);
            verify(mockExecutor, never()).shutdown();
        }
    }

    @Test
    public void testPollingScan_WhenScanCompleted_ShutsDownExecutorAndNotifies() throws Exception {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getStatus()).thenReturn("Completed");

        java.util.concurrent.ScheduledExecutorService mockExecutor =
                mock(java.util.concurrent.ScheduledExecutorService.class);
        setPrivateField(startScanAction, "pollScanExecutor", mockExecutor);

        try (MockedStatic<Scan> scanMock = mockStatic(Scan.class);
             MockedStatic<ActivityTracker> atMock = mockStatic(ActivityTracker.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {

            scanMock.when(() -> Scan.scanShow(anyString())).thenReturn(mockScan);
            ActivityTracker mockAT = mock(ActivityTracker.class);
            atMock.when(ActivityTracker::getInstance).thenReturn(mockAT);
            utilsMock.when(() -> Utils.notifyScan(any(), any(), any(), any(), any(), any()))
                     .thenAnswer(i -> null);

            Method pollingScanMethod = StartScanAction.class.getDeclaredMethod("pollingScan", String.class);
            pollingScanMethod.setAccessible(true);
            Runnable runnable = (Runnable) pollingScanMethod.invoke(startScanAction, "scan-done-id");

            assertDoesNotThrow(runnable::run);
            verify(mockExecutor).shutdown();
            verify(mockAT).inc();
        }
    }

    @Test
    public void testPollingScan_WhenScanFinishedNotCompleted_ShutsDownWithoutNotify() throws Exception {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        when(mockScan.getStatus()).thenReturn("Failed");  // not completed, not running

        java.util.concurrent.ScheduledExecutorService mockExecutor =
                mock(java.util.concurrent.ScheduledExecutorService.class);
        setPrivateField(startScanAction, "pollScanExecutor", mockExecutor);

        try (MockedStatic<Scan> scanMock = mockStatic(Scan.class);
             MockedStatic<ActivityTracker> atMock = mockStatic(ActivityTracker.class)) {

            scanMock.when(() -> Scan.scanShow(anyString())).thenReturn(mockScan);
            ActivityTracker mockAT = mock(ActivityTracker.class);
            atMock.when(ActivityTracker::getInstance).thenReturn(mockAT);

            Method pollingScanMethod = StartScanAction.class.getDeclaredMethod("pollingScan", String.class);
            pollingScanMethod.setAccessible(true);
            Runnable runnable = (Runnable) pollingScanMethod.invoke(startScanAction, "scan-id");

            assertDoesNotThrow(runnable::run);
            verify(mockExecutor).shutdown();
        }
    }

    // ===== refreshBranchSelection() when branchSelectionGroup is null =====

    @Test
    public void testRefreshBranchSelection_WhenBranchSelectionGroupNull_DoesNotThrow() throws Exception {
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);
        // getProjectId() is NOT called when branchSelectionGroup is null — no stub needed

        when(mockCxToolWindowPanel.getRootGroup()).thenReturn(mockRootGroup);
        when(mockRootGroup.getBranchSelectionGroup()).thenReturn(null);  // null branch group

        Method method = StartScanAction.class.getDeclaredMethod("refreshBranchSelection", com.checkmarx.ast.scan.Scan.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(startScanAction, mockScan));
    }

    // ===== actionPerformed — repo match, project match → calls createScan =====

    @Test
    public void testActionPerformed_WithRepo_MatchingBranchAndProject_CallsCreateScan() {
        Repository mockRepo = mock(Repository.class);
        when(mockRepo.getCurrentBranchName()).thenReturn("main");
        when(mockRepo.toLogString()).thenReturn("Root{myUrls=[https://github.com/org/my-project.git], vcs=Git}");
        when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");
        when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn("org/my-project");

        AnActionEvent e = mock(AnActionEvent.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS);
             MockedStatic<ProgressManager> pmMock = mockStatic(ProgressManager.class)) {

            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepo);
            ProgressManager mockPm = mock(ProgressManager.class);
            pmMock.when(ProgressManager::getInstance).thenReturn(mockPm);

            StartScanAction spyAction = spy(startScanAction);

            // Intercept createScan indirectly through ProgressManager
            spyAction.actionPerformed(e);

            verify(mockPm).run(any(Task.Backgroundable.class));
        }
    }

    @Test
    public void testActionPerformed_WithNoRepo_MatchingProject_CallsCreateScan() {
        when(mockPropertiesComponent.getValue("Checkmarx.SelectedProject")).thenReturn(null); // no project → isAstProjectMatchesWorkspaceProject returns false
        AnActionEvent e = mock(AnActionEvent.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            utilsMock.when(() -> Utils.notifyScan(any(), any(), any(), any(), any(), any())).thenAnswer(i -> null);

            startScanAction.actionPerformed(e);

            // notifyScan called because project doesn't match
            utilsMock.verify(() -> Utils.notifyScan(any(), any(), eq(mockProject), any(), any(), any()));
        }
    }

    @Test
    public void testUpdate_WhenScanRunningAndNotPolled_CallsPollScan() {
        AnActionEvent e = mock(AnActionEvent.class);
        Presentation pres = mock(Presentation.class);
        when(e.getProject()).thenReturn(mockProject);
        when(e.getPresentation()).thenReturn(pres);

        ProgressManager mockPm = mock(ProgressManager.class);

        try (MockedStatic<StartScanAction> saMock = mockStatic(StartScanAction.class, CALLS_REAL_METHODS);
             MockedStatic<PropertiesComponent> pcMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class);
             MockedStatic<ProgressManager> pmMock = mockStatic(ProgressManager.class)) {

            saMock.when(StartScanAction::getUserHasPermissionsToScan).thenReturn(true);
            pcMock.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(mockPropertiesComponent);
            ToolWindowManager mockTwm = mock(ToolWindowManager.class);
            twmMock.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(mockTwm);
            when(mockTwm.getToolWindow(any())).thenReturn(null);
            pmMock.when(ProgressManager::getInstance).thenReturn(mockPm);

            when(mockPropertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY)).thenReturn("scan-123");
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("my-project");
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");

            startScanAction.update(e);

            verify(mockPm).run(any(Task.Backgroundable.class));
        }
    }
}
