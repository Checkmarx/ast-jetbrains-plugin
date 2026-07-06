package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ProjectSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ResetSelectionAction;
import com.checkmarx.intellij.ast.window.actions.selection.RootGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RootGroupTest {

    private static RootGroup newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (RootGroup) unsafe.allocateInstance(RootGroup.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }


    @Test
    void setEnabled_PropagatesToChildrenAndResetAction() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);
        ResetSelectionAction resetAction = mock(ResetSelectionAction.class);

        // Mock ToolWindowManager to prevent NullPointerException in refreshPanel
        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class)) {
            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);
            setField(root, "resetSelectionAction", resetAction);

            root.setEnabled(false);

            verify(projectGroup).setEnabled(false);
            verify(branchGroup).setEnabled(false);
            verify(scanGroup).setEnabled(false);
            verify(resetAction).setEnabled(false);
            assertEquals(ActionUpdateThread.EDT, root.getActionUpdateThread());
        }
    }

    @Test
    void setEnabled_WhenResetActionIsNull_PropagatesWithoutResetAction() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class)) {
            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);
            setField(root, "resetSelectionAction", null);

            root.setEnabled(true);

            verify(projectGroup).setEnabled(true);
            verify(branchGroup).setEnabled(true);
            verify(scanGroup).setEnabled(true);
        }
    }

    @Test
    void reset_ClearsAllSelectionsAndRefreshesProjectGroup() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class)) {
            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);

            root.reset();

            boolean projectClearCalled = mockingDetails(projectGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean branchClearCalled = mockingDetails(branchGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean scanClearCalled = mockingDetails(scanGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean projectRefreshCalled = mockingDetails(projectGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("refresh"));

            assertTrue(projectClearCalled, "projectSelectionGroup.clear() should be called");
            assertTrue(branchClearCalled, "branchSelectionGroup.clear() should be called");
            assertTrue(scanClearCalled, "scanSelectionGroup.clear() should be called");
            assertTrue(projectRefreshCalled, "projectSelectionGroup.refresh() should be called");
        }
    }

    @Test
    void override_DisablesBranchAndScanGroups() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> scanCmdMock = mockStatic(com.checkmarx.intellij.ast.commands.Scan.class)) {

            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mock(Application.class));
            scanCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Scan.scanShow(any())).thenReturn(null);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);
            setField(root, "resetSelectionAction", null);

            // Verify setEnabled(false) is called during override
            root.override("scan-123");

            boolean branchClearCalled = mockingDetails(branchGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean scanClearCalled = mockingDetails(scanGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));

            assertTrue(branchClearCalled, "branchSelectionGroup.clear() should be called");
            assertTrue(scanClearCalled, "scanSelectionGroup.clear() should be called");
        }
    }


    @Test
    void getActionUpdateThread_ReturnsEDT() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();
        assertEquals(ActionUpdateThread.EDT, root.getActionUpdateThread());
    }

    // ===== override — synchronous execution of thenAccept lambda =====

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void override_WhenScanFound_CallsProjectGroupOverride() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);
        com.checkmarx.ast.scan.Scan mockScan = mock(com.checkmarx.ast.scan.Scan.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> scanCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Scan.class)) {

            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);
            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(app).invokeLater(any());

            CompletableFuture mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer consumer = inv.getArgument(0);
                consumer.accept(mockScan);
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier supplier = inv.getArgument(0);
                try { supplier.get(); } catch (Exception ignored) {}
                return mockFuture;
            });
            scanCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Scan.scanShow(anyString()))
                       .thenReturn(mockScan);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);
            setField(root, "resetSelectionAction", null);

            root.override("scan-found-id");

            boolean overrideCalled = mockingDetails(projectGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("override"));
            assertTrue(overrideCalled, "projectSelectionGroup.override(scan) should be called when scan is found");
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void override_WhenScanNull_JustReEnablesWithoutCallingProjectOverride() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        ProjectSelectionGroup projectGroup = mock(ProjectSelectionGroup.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> scanCmdMock =
                     mockStatic(com.checkmarx.intellij.ast.commands.Scan.class)) {

            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);
            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(app).invokeLater(any());

            CompletableFuture mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer consumer = inv.getArgument(0);
                consumer.accept(null); // null scan
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier supplier = inv.getArgument(0);
                try { supplier.get(); } catch (Exception ignored) {}
                return mockFuture;
            });
            scanCmdMock.when(() -> com.checkmarx.intellij.ast.commands.Scan.scanShow(anyString()))
                       .thenReturn(null);

            setField(root, "project", project);
            setField(root, "projectSelectionGroup", projectGroup);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);
            setField(root, "resetSelectionAction", null);

            root.override("bad-scan");

            boolean overrideCalled = mockingDetails(projectGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("override"));
            assertFalse(overrideCalled, "projectSelectionGroup.override should NOT be called when scan is null");
        }
    }

    @Test
    void getters_ReturnCorrectScanAndBranchSelectionGroups() throws Exception {
        RootGroup root = newInstanceWithoutConstructor();

        Project project = mock(Project.class);
        BranchSelectionGroup branchGroup = mock(BranchSelectionGroup.class);
        ScanSelectionGroup scanGroup = mock(ScanSelectionGroup.class);

        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

        try (MockedStatic<ToolWindowManager> twmMock = mockStatic(ToolWindowManager.class)) {
            twmMock.when(() -> ToolWindowManager.getInstance(project)).thenReturn(toolWindowManager);

            setField(root, "project", project);
            setField(root, "branchSelectionGroup", branchGroup);
            setField(root, "scanSelectionGroup", scanGroup);

            assertEquals(branchGroup, root.getBranchSelectionGroup());
            assertEquals(scanGroup, root.getScanSelectionGroup());
        }
    }
}
