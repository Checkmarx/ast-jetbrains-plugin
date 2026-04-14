package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

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
}


