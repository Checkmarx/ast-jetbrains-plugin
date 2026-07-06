package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.BranchChangeListener;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchSelectionGroupTest {

    @Mock
    private Project mockProject;

    @Mock
    private ScanSelectionGroup mockScanSelectionGroup;

    @Mock
    private PropertiesComponent mockPropertiesComponent;


    @Captor
    private ArgumentCaptor<BranchChangeListener> branchChangeListenerCaptor;

    private BranchSelectionGroup branchSelectionGroup;

    @BeforeEach
    void setUp() {
        lenient().when(mockProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);

        MessageBus messageBus = mock(MessageBus.class);
        MessageBusConnection messageBusConnection = mock(MessageBusConnection.class);
        lenient().when(mockProject.getMessageBus()).thenReturn(messageBus);
        lenient().when(messageBus.connect()).thenReturn(messageBusConnection);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        lenient().when(mockProject.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        branchSelectionGroup = spy(new BranchSelectionGroup(mockProject, mockScanSelectionGroup));
        lenient().doNothing().when(branchSelectionGroup).refreshPanel(mockProject);
    }

    /** Helper: Inject the branches list into a private field. */
    private void setBranches(List<String> branches) throws Exception {
        Field f = BranchSelectionGroup.class.getDeclaredField("branches");
        f.setAccessible(true);
        f.set(branchSelectionGroup, branches);
    }

    /** Helper: Inject projectId into a private field. */
    private void setProjectId(String projectId) throws Exception {
        Field f = BranchSelectionGroup.class.getDeclaredField("projectId");
        f.setAccessible(true);
        f.set(branchSelectionGroup, projectId);
    }

    /** Helper: Get BranchChangeListener from the constructor. */
    private BranchChangeListener captureBranchChangeListener() {
        verify(mockProject.getMessageBus().connect()).subscribe(
            eq(BranchChangeListener.VCS_BRANCH_CHANGED),
            branchChangeListenerCaptor.capture()
        );
        return branchChangeListenerCaptor.getValue();
    }

    @Test
    void testConstructorInitializesAndGetActiveBranch() {
        // Verify MessageBus subscription for BranchChangeListener
        verify(mockProject.getMessageBus().connect()).subscribe(
            eq(BranchChangeListener.VCS_BRANCH_CHANGED),
            any(BranchChangeListener.class)
        );

        // Test getActiveBranch with no repository
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            assertNull(branchSelectionGroup.getActiveBranch(), "Should return null when no root repository");
        }

        // Test getActiveBranch with repository
        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCurrentBranchName()).thenReturn("main");
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepository);
            assertEquals("main", branchSelectionGroup.getActiveBranch(), "Should return branch name from repository");
        }
    }

    @Test
    void testClearAndOverride() throws Exception {
        // Test clear() - resets all state
        setBranches(List.of("main", "develop"));
        setProjectId("test-project-id");

        Method clearMethod = BranchSelectionGroup.class.getDeclaredMethod("clear");
        clearMethod.setAccessible(true);
        clearMethod.invoke(branchSelectionGroup);

        // Verify state is cleared
        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, null);
        assertTrue(
            mockingDetails(mockScanSelectionGroup).getInvocations().stream()
                .anyMatch(inv -> inv.getMethod().getName().equals("clear")),
            "Expected ScanSelectionGroup.clear() to be invoked"
        );

        // Verify internal state reset (branches and projectId)
        Field branchesField = BranchSelectionGroup.class.getDeclaredField("branches");
        branchesField.setAccessible(true);
        assertEquals(Collections.emptyList(), branchesField.get(branchSelectionGroup));

        Field projectIdField = BranchSelectionGroup.class.getDeclaredField("projectId");
        projectIdField.setAccessible(true);
        assertNull(projectIdField.get(branchSelectionGroup));

        // Test override(Scan) - stores branch and delegates
        reset(mockPropertiesComponent, mockScanSelectionGroup, branchSelectionGroup);
        Scan mockScan = mock(Scan.class);
        when(mockScan.getBranch()).thenReturn("feature/new-branch");

        Method overrideMethod = BranchSelectionGroup.class.getDeclaredMethod("override", Scan.class);
        overrideMethod.setAccessible(true);
        overrideMethod.invoke(branchSelectionGroup, mockScan);

        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "feature/new-branch");
        assertTrue(
            mockingDetails(mockScanSelectionGroup).getInvocations().stream()
                .anyMatch(inv -> inv.getMethod().getName().equals("override")),
            "Expected ScanSelectionGroup.override(scan) to be invoked"
        );
    }

    @Test
    void testSetDefaultBranch() throws Exception {
        Method setDefaultBranchMethod = BranchSelectionGroup.class.getDeclaredMethod("setDefaultBranch");
        setDefaultBranchMethod.setAccessible(true);

        // Scenario 1: Empty branches list
        setBranches(Collections.emptyList());
        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("none", result, "Should return 'none' when branches list is empty");

        // Scenario 2: Active branch exists and is in the list
        reset(mockPropertiesComponent);
        setBranches(List.of("main", "develop", "staging"));
        doReturn("main").when(branchSelectionGroup).getActiveBranch();
        result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("main", result, "Should return active branch when in list");
        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "main");

        // Scenario 3: Active branch not in a list, falls back to the local branch
        reset(mockPropertiesComponent);
        setBranches(List.of("main", "develop"));
        doReturn("feature/unknown").when(branchSelectionGroup).getActiveBranch();
        result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("main", result, "Should return first branch when active branch not in list");
        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, Constants.USE_LOCAL_BRANCH);
    }

    @Test
    void testGetTitle() throws Exception {
        // Test getTitle() returns a string without errors
        Method getTitleMethod = BranchSelectionGroup.class.getDeclaredMethod("getTitle");
        getTitleMethod.setAccessible(true);

        // Scenario 1: No stored branch - should return by default
        branchSelectionGroup.setEnabled(false);
        lenient().when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn(null);
        String title = (String) getTitleMethod.invoke(branchSelectionGroup);
        assertNotNull(title, "Title should not be null");
        assertFalse(title.isEmpty(), "Title should not be empty");

        // Scenario 2: With stored branch - should include the branch name
        reset(mockPropertiesComponent);
        lenient().when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("develop");
        title = (String) getTitleMethod.invoke(branchSelectionGroup);
        assertNotNull(title, "Title should not be null");
        assertFalse(title.isEmpty(), "Title should not be empty");
    }

    @Test
    void testBranchChangeListener() throws Exception {
        setProjectId("project-123");
        setBranches(List.of("main", "develop", "feature/test"));

        Repository mockRepository = mock(Repository.class);
        BranchChangeListener listener = captureBranchChangeListener();

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            // Setup mocks for all scenarios
            lenient().when(mockRepository.getCurrentBranchName()).thenReturn("develop");
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepository);
            lenient().when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");

            // Scenario 1: branchWillChange - Should just log (no-op)
            listener.branchWillChange("develop");

            // Scenario 2: branchHasChanged with a valid project and branches
            listener.branchHasChanged("main");

            // Scenario 3: branchHasChanged with different branch
            listener.branchHasChanged("develop");

            // All scenarios should complete without throwing exceptions
            assertNotNull(listener, "Listener should not be null");
        }
    }

    @Test
    void testBranchChangeListener_WhenProjectIdNull_ReturnsEarly() throws Exception {
        setProjectId(null);
        BranchChangeListener listener = newOnBranchChange();

        listener.branchHasChanged("develop");

        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    @Test
    void testBranchChangeListener_WhenBranchesEmpty_ReturnsEarly() throws Exception {
        setProjectId("project-123");
        setBranches(Collections.emptyList());
        BranchChangeListener listener = newOnBranchChange();

        listener.branchHasChanged("develop");

        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    @Test
    void testBranchChangeListener_WhenActiveBranchNull_ReturnsEarly() throws Exception {
        setProjectId("project-123");
        setBranches(List.of("main", "develop"));
        BranchChangeListener listener = newOnBranchChange();

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);
            listener.branchHasChanged("develop");
        }

        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    @Test
    void testBranchChangeListener_WhenBranchEqualsCurrentSelected_ReturnsEarly() throws Exception {
        setProjectId("project-123");
        setBranches(List.of("main", "develop"));
        BranchChangeListener listener = newOnBranchChange();

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCurrentBranchName()).thenReturn("develop");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepository);
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("develop");

            listener.branchHasChanged("develop");
        }

        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    @Test
    void testBranchChangeListener_WhenBranchMatchesAvailableBranch_RefreshesScans() throws Exception {
        setProjectId("project-123");
        setBranches(List.of("main", "develop"));
        BranchChangeListener listener = newOnBranchChange();

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCurrentBranchName()).thenReturn("develop");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepository);
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");

            listener.branchHasChanged("develop");
        }

        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "develop");
        verify(mockScanSelectionGroup).refresh(eq("project-123"), eq("develop"), eq(true));
    }

    @Test
    void testBranchChangeListener_WhenNoMatchingBranch_DoesNotRefresh() throws Exception {
        setProjectId("project-123");
        setBranches(List.of("main", "release"));
        BranchChangeListener listener = newOnBranchChange();

        Repository mockRepository = mock(Repository.class);
        when(mockRepository.getCurrentBranchName()).thenReturn("feature/unknown");

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(mockRepository);
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("main");

            listener.branchHasChanged("feature/unknown");
        }

        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    @Test
    void testBranchChangeListener_BranchWillChange_IsNoOp() throws Exception {
        BranchChangeListener listener = newOnBranchChange();
        assertDoesNotThrow(() -> listener.branchWillChange("main"));
        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
    }

    // ===== BranchSelectionGroup$Action inner class =====

    @SuppressWarnings("unchecked")
    private AnAction newBranchAction(String projectId, String branch) throws Exception {
        Class<?> actionClass = Class.forName("com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup$Action");
        Constructor<?> ctor = actionClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return (AnAction) ctor.newInstance(branchSelectionGroup, projectId, branch);
    }

    private BranchChangeListener newOnBranchChange() throws Exception {
        Class<?> cls = Class.forName("com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup$OnBranchChange");
        Constructor<?> ctor = cls.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        return (BranchChangeListener) ctor.newInstance(branchSelectionGroup);
    }

    @Test
    void branchAction_ActionPerformed_NonLocalBranch_RefreshesScanGroup() throws Exception {
        AnAction action = newBranchAction("project-42", "develop");
        AnActionEvent e = mock(AnActionEvent.class);

        action.actionPerformed(e);

        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "develop");
        assertTrue(
            mockingDetails(mockScanSelectionGroup).getInvocations().stream()
                .anyMatch(inv -> inv.getMethod().getName().equals("clear")),
            "Expected ScanSelectionGroup.clear() to be invoked"
        );
        verify(mockScanSelectionGroup).refresh(eq("project-42"), eq("develop"), eq(true));
    }

    @Test
    void branchAction_ActionPerformed_LocalBranch_RefreshesPanelNotScanGroup() throws Exception {
        AnAction action = newBranchAction("project-42", Constants.USE_LOCAL_BRANCH);
        AnActionEvent e = mock(AnActionEvent.class);

        action.actionPerformed(e);

        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, Constants.USE_LOCAL_BRANCH);
        assertTrue(
            mockingDetails(mockScanSelectionGroup).getInvocations().stream()
                .anyMatch(inv -> inv.getMethod().getName().equals("clear")),
            "Expected ScanSelectionGroup.clear() to be invoked"
        );
        verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
        verify(branchSelectionGroup).refreshPanel(mockProject);
    }

    // ===== refresh() body tests =====

    /** Helper to mock CompletableFuture.supplyAsync to run supplier + call consumer with given branches. */
    @SuppressWarnings({"rawtypes","unchecked"})
    private void mockCompletableFutureForBranches(MockedStatic<CompletableFuture> cfMock,
                                                   Application app,
                                                   List<String> branches) {
        CompletableFuture mockFuture = mock(CompletableFuture.class);
        when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
            Consumer<List<String>> consumer = inv.getArgument(0);
            consumer.accept(branches);
            return null;
        });
        cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
            Supplier<List<String>> supplier = inv.getArgument(0);
            try { supplier.get(); } catch (Exception ignored) {}
            return mockFuture;
        });
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(app).invokeLater(any(Runnable.class));
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void refresh_WhenActiveBranchMatchesList_SelectsActiveBranchAndRefreshesScans() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {

            amMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));

            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            mockCompletableFutureForBranches(cfMock, app, List.of("main", "develop"));

            // getBranches is called in the supplier — return something valid
            projCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Project.getBranches(any(), anyBoolean()))
                    .thenReturn(List.of("main", "develop"));
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);

            // stored branch is something else; active branch is "main" → should be picked up
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("develop");
            doReturn("main").when(branchSelectionGroup).getActiveBranch();

            branchSelectionGroup.refresh("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", true);

            // Verify active branch was stored and scan group refreshed
            verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, "main");
            verify(mockScanSelectionGroup).refresh(eq("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e"), eq("main"), eq(false));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void refresh_WhenStoredBranchMatchesButActiveBranchDoesNot_RefreshesForStoredBranch() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {

            amMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));

            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            mockCompletableFutureForBranches(cfMock, app, List.of("main", "develop"));

            projCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Project.getBranches(any(), anyBoolean()))
                    .thenReturn(List.of("main", "develop"));
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);

            // active branch is "feature/x" (not in list); stored branch "develop" is in the list
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("develop");
            doReturn("feature/x").when(branchSelectionGroup).getActiveBranch();

            branchSelectionGroup.refresh("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", false);

            verify(mockScanSelectionGroup).refresh(eq("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e"), eq("develop"), eq(false));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void refresh_WhenNoBranchMatches_EnablesPanelWithoutScanRefresh() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {

            amMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));

            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            mockCompletableFutureForBranches(cfMock, app, List.of("main", "develop"));

            projCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Project.getBranches(any(), anyBoolean()))
                    .thenReturn(List.of("main", "develop"));
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);

            // Neither active nor stored branch matches
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY)).thenReturn("other");
            doReturn("feature/xyz").when(branchSelectionGroup).getActiveBranch();

            branchSelectionGroup.refresh("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", true);

            verify(mockScanSelectionGroup, never()).refresh(any(), any(), anyBoolean());
            verify(branchSelectionGroup, atLeastOnce()).refreshPanel(mockProject);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void refresh_WhenStoredBranchIsUseLocalBranch_SkipsElseIfBody() throws Exception {
        // storedBranch == USE_LOCAL_BRANCH → the else-if condition `!branch.equals(USE_LOCAL_BRANCH)` is false → skip body
        Application app = mock(Application.class);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<ActionManager> amMock = mockStatic(ActionManager.class)) {

            amMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            mockCompletableFutureForBranches(cfMock, app, List.of(Constants.USE_LOCAL_BRANCH, "main"));

            projCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Project.getBranches(any(), anyBoolean()))
                    .thenReturn(List.of(Constants.USE_LOCAL_BRANCH, "main"));
            utilsMock.when(() -> Utils.getRootRepository(mockProject)).thenReturn(null);

            // storedBranch = USE_LOCAL_BRANCH, activeBranch = "main" (not in list as first entry)
            when(mockPropertiesComponent.getValue(Constants.SELECTED_BRANCH_PROPERTY))
                    .thenReturn(Constants.USE_LOCAL_BRANCH);
            doReturn("feature/xyz").when(branchSelectionGroup).getActiveBranch();

            branchSelectionGroup.refresh("3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e", false);

            // The else-if branch `branch.equals(storedBranch) && !branch.equals(USE_LOCAL_BRANCH)` is false
            // because branch==USE_LOCAL_BRANCH → scanSelectionGroup.refresh() should NOT be called for that branch
            verify(mockScanSelectionGroup, never()).refresh(any(), eq(Constants.USE_LOCAL_BRANCH), anyBoolean());
        }
    }

    @Test
    void setDefaultBranch_WhenActiveBranchNull_ReturnsNoneSelected() throws Exception {
        Method setDefaultBranchMethod = BranchSelectionGroup.class.getDeclaredMethod("setDefaultBranch");
        setDefaultBranchMethod.setAccessible(true);

        setBranches(List.of("main", "develop"));
        doReturn(null).when(branchSelectionGroup).getActiveBranch();

        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);
        assertEquals("none", result, "Should return NONE_SELECTED when active branch is null");
        // PropertiesComponent should NOT be written
        verify(mockPropertiesComponent, never()).setValue(eq(Constants.SELECTED_BRANCH_PROPERTY), anyString());
    }

    @Test
    void setDefaultBranch_WhenActiveBranchNotInList_SetsLocalBranchAndReturnsFirst() throws Exception {
        Method setDefaultBranchMethod = BranchSelectionGroup.class.getDeclaredMethod("setDefaultBranch");
        setDefaultBranchMethod.setAccessible(true);

        setBranches(List.of("main", "develop"));
        doReturn("feature/new").when(branchSelectionGroup).getActiveBranch();

        String result = (String) setDefaultBranchMethod.invoke(branchSelectionGroup);

        assertEquals("main", result, "Should return first branch when active branch not in list");
        verify(mockPropertiesComponent).setValue(Constants.SELECTED_BRANCH_PROPERTY, Constants.USE_LOCAL_BRANCH);
    }
}


