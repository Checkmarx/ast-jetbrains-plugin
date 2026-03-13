package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.checkmarx.intellij.common.utils.Constants;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"rawtypes", "unchecked"})
class ScanSelectionGroupTest {

    @Mock
    private Project mockProject;

    private static class TestScanSelectionGroup extends ScanSelectionGroup {
        TestScanSelectionGroup(@NotNull Project project) {
            super(project);
        }
        @Override
        public void refreshPanel(@NotNull Project project) { /* no-op */ }
    }

    private static Scan mockScan(String id, String createdAt) {
        Scan scan = mock(Scan.class);
        when(scan.getId()).thenReturn(id);
        when(scan.getCreatedAt()).thenReturn(createdAt);
        return scan;
    }

    private static Object invoke(Object target, String method, Class<?>[] sig, Object... args) throws Exception {
        Method m = ScanSelectionGroup.class.getDeclaredMethod(method, sig);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    /** Returns a MockedStatic<CompletableFuture> that executes supplyAsync + thenAccept synchronously. */
    private static MockedStatic<CompletableFuture> syncCf() {
        MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
        cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
            Supplier supplier = inv.getArgument(0);
            Object result = supplier.get();
            CompletableFuture fut = mock(CompletableFuture.class);
            when(fut.thenAccept(any())).thenAnswer(inv2 -> {
                Consumer consumer = inv2.getArgument(0);
                consumer.accept(result);
                return mock(CompletableFuture.class);
            });
            return fut;
        });
        return cfMock;
    }

    /** Stubs app.invokeLater() to run the Runnable immediately. */
    private static Application syncApp() {
        Application app = mock(Application.class);
        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                .when(app).invokeLater(any());
        return app;
    }

    @Test
    void formatUnformatOverrideClear_CoverCoreStateMethods() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(null);

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class)) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(mock(Application.class));

            TestScanSelectionGroup group = new TestScanSelectionGroup(mockProject);
            Scan scan = mockScan("scan-abc", "2024-05-01T10:30:45");

            // formatScan (latest=true) and unFormatScan round-trip
            String formatted = (String) invoke(group, "formatScan",
                    new Class<?>[]{Scan.class, boolean.class}, scan, true);
            assertTrue(formatted.contains("scan-abc"));
            assertEquals("scan-abc",
                    invoke(group, "unFormatScan", new Class<?>[]{String.class}, formatted));

            // override stores non-latest formatted value
            String nonLatest = (String) invoke(group, "formatScan",
                    new Class<?>[]{Scan.class, boolean.class}, scan, false);
            invoke(group, "override", new Class<?>[]{com.checkmarx.ast.scan.Scan.class}, scan);
            verify(props).setValue(Constants.SELECTED_SCAN_PROPERTY, nonLatest);

            // clear nulls the property
            invoke(group, "clear", new Class<?>[]{});
            verify(props).setValue(Constants.SELECTED_SCAN_PROPERTY, null);
        }
    }

    @Test
    void getTitle_WhenDisabledAndEmpty_ShowsEllipsis() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(null);

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class)) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(mock(Application.class));

            TestScanSelectionGroup group = new TestScanSelectionGroup(mockProject);
            group.setEnabled(false);

            String title = (String) invoke(group, "getTitle", new Class<?>[]{});
            assertTrue(title.contains("..."));
        }
    }

    @Test
    void getTitle_WhenEnabledAndEmpty_ShowsNoneSelected() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(null);

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class)) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(mock(Application.class));

            TestScanSelectionGroup group = new TestScanSelectionGroup(mockProject);
            group.setEnabled(true);

            String title = (String) invoke(group, "getTitle", new Class<?>[]{});
            // should NOT contain "..."
            assertFalse(title.contains("..."));
        }
    }

    @Test
    void refresh_WithScansAndSelectLatest_PopulatesAndSelectsFirstScan() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(null);

        Application app = syncApp();
        Scan scan1 = mockScan("scan-1", "2024-05-01T10:30:45");
        Scan scan2 = mockScan("scan-2", "2024-05-02T11:00:00");
        List<Scan> scans = List.of(scan1, scan2);

        CxToolWindowPanel panel = mock(CxToolWindowPanel.class);
        ActionManager actionManager = mock(ActionManager.class);

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class);
             MockedStatic<ActionManager> amgr = mockStatic(ActionManager.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Scan> sc =
                     mockStatic(com.checkmarx.intellij.ast.commands.Scan.class);
             MockedStatic<CompletableFuture> cf = syncCf()) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(app);
            amgr.when(ActionManager::getInstance).thenReturn(actionManager);
            sc.when(() -> com.checkmarx.intellij.ast.commands.Scan.getList("p1", "main"))
              .thenReturn(scans);

            TestScanSelectionGroup group = spy(new TestScanSelectionGroup(mockProject));
            doReturn(panel).when(group).getCxToolWindowPanel(mockProject);
            doNothing().when(group).refreshPanel(mockProject);

            group.refresh("p1", "main", true);

            // select() was called → panel.selectScan invoked with first scan id
            verify(panel).selectScan("scan-1");
            // group became enabled after async
            assertTrue(group.isEnabled());
            // two children added
            assertEquals(2, group.getChildrenCount());

            // getTitle with stored scan (non-blank)
            when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn("2024/05/01 10:30:45  scan-1");
            String title = (String) invoke(group, "getTitle", new Class<?>[]{});
            assertTrue(title.contains("scan-1"));
        }
    }

    // ── 5. refresh() with empty list (blank inputs → emptyList) ──────────────
    //    covers: isBlank branch returning emptyList, and selectLatest=false path
    @Test
    void refresh_WithBlankInputs_ReturnsEmptyAndEnables() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(null);

        Application app = syncApp();

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cf = syncCf()) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(app);

            TestScanSelectionGroup group = spy(new TestScanSelectionGroup(mockProject));
            doNothing().when(group).refreshPanel(mockProject);

            group.refresh("", "", false);

            assertTrue(group.isEnabled());
            assertEquals(0, group.getChildrenCount());
        }
    }

    @Test
    void constructorStoredScan_AndActionPerformed_CoverRemainingPaths() throws Exception {
        PropertiesComponent props = mock(PropertiesComponent.class);
        String storedFormatted = "2024/05/01 10:30:45  scan-xyz";
        when(props.getValue(Constants.SELECTED_SCAN_PROPERTY)).thenReturn(storedFormatted);

        Application app = syncApp();
        CxToolWindowPanel panel = mock(CxToolWindowPanel.class);
        ToolWindowManager toolWindowManager = mock(ToolWindowManager.class);
        ActionManager actionManager = mock(ActionManager.class);

        // Build a ToolWindow → content → component chain so getCxToolWindowPanel returns panel
        com.intellij.openapi.wm.ToolWindow toolWindow = mock(com.intellij.openapi.wm.ToolWindow.class);
        com.intellij.ui.content.ContentManager contentManager = mock(com.intellij.ui.content.ContentManager.class);
        com.intellij.ui.content.Content content = mock(com.intellij.ui.content.Content.class);
        when(toolWindowManager.getToolWindow(any())).thenReturn(toolWindow);
        when(toolWindow.getContentManager()).thenReturn(contentManager);
        when(contentManager.getContents()).thenReturn(new com.intellij.ui.content.Content[]{content});
        when(content.getDisplayName()).thenReturn("Scan Results");
        when(content.getComponent()).thenReturn(panel);

        try (MockedStatic<PropertiesComponent> pm = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> am = mockStatic(ApplicationManager.class);
             MockedStatic<ToolWindowManager> twm = mockStatic(ToolWindowManager.class);
             MockedStatic<ActionManager> amgr = mockStatic(ActionManager.class)) {

            pm.when(() -> PropertiesComponent.getInstance(mockProject)).thenReturn(props);
            am.when(ApplicationManager::getApplication).thenReturn(app);
            twm.when(() -> ToolWindowManager.getInstance(mockProject)).thenReturn(toolWindowManager);
            amgr.when(ActionManager::getInstance).thenReturn(actionManager);
            when(mockProject.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);

            // Constructor fires invokeLater synchronously → getCxToolWindowPanel → panel.selectScan
            TestScanSelectionGroup group = new TestScanSelectionGroup(mockProject);

            verify(panel).selectScan("scan-xyz");

            // ── Action.actionPerformed ────────────────────────────────────────
            Class<?> actionClass = null;
            for (Class<?> c : ScanSelectionGroup.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("Action")) { actionClass = c; break; }
            }
            assertNotNull(actionClass);
            java.lang.reflect.Constructor<?> ctor =
                    actionClass.getDeclaredConstructor(ScanSelectionGroup.class, String.class, String.class);
            ctor.setAccessible(true);
            Object action = ctor.newInstance(group, "scan-xyz", storedFormatted);

            AnActionEvent event = mock(AnActionEvent.class);
            Method actionPerformed = actionClass.getDeclaredMethod("actionPerformed", AnActionEvent.class);
            actionPerformed.setAccessible(true);
            actionPerformed.invoke(action, event);

            // select() stores property and calls panel.selectScan again
            verify(props, atLeastOnce()).setValue(Constants.SELECTED_SCAN_PROPERTY, storedFormatted);
            verify(panel, atLeast(2)).selectScan("scan-xyz");
        }
    }
}
