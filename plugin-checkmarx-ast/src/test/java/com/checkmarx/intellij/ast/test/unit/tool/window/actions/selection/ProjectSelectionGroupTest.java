package com.checkmarx.intellij.ast.test.unit.tool.window.actions.selection;

import com.checkmarx.ast.scan.Scan;
import com.checkmarx.intellij.ast.window.actions.selection.BranchSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ProjectSelectionGroup;
import com.checkmarx.intellij.ast.window.actions.selection.ResetSelectionAction;
import com.checkmarx.intellij.ast.window.actions.selection.ScanSelectionGroup;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.dvcs.repo.Repository;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.intellij.openapi.actionSystem.ActionManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"rawtypes", "unchecked"})
class ProjectSelectionGroupTest {

    @Mock
    private Project mockIdeProject;

    @Mock
    private BranchSelectionGroup mockBranchSelectionGroup;

    @Mock
    private ScanSelectionGroup mockScanSelectionGroup;

    @Mock
    private ResetSelectionAction mockResetSelectionAction;

    @Mock
    private PropertiesComponent mockPropertiesComponent;

    private ProjectSelectionGroup projectSelectionGroup;

    private MockedStatic<ActionManager> actionManagerMock;

    @BeforeEach
    void setUp() {
        lenient().when(mockIdeProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);
        actionManagerMock = mockStatic(ActionManager.class);
        actionManagerMock.when(ActionManager::getInstance).thenReturn(mock(ActionManager.class));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (actionManagerMock != null) {
            actionManagerMock.close();
        }
    }

    private static final String UUID_INHERIT   = "3f6a5b2c-1d4e-4f8a-9c0b-7e2d1a3f5c8e";
    private static final String UUID_STORED    = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String UUID_RESET     = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String UUID_BLANK     = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UUID_TITLE     = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final String UUID_ACTION    = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

    private com.checkmarx.ast.project.Project createAstProject(String id, String name) {
        com.checkmarx.ast.project.Project p = mock(com.checkmarx.ast.project.Project.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        return p;
    }

    @Test
    void clear_RemovesStoredProjectProperty() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd = mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock = mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);

            // Mock ToolWindowManager
            com.intellij.openapi.wm.ToolWindowManager toolWindowManager = mock(com.intellij.openapi.wm.ToolWindowManager.class);
            twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject)).thenReturn(toolWindowManager);

            // Mock CompletableFuture chain to execute synchronously
            CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
                consumer.accept(Collections.emptyList());
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
                supplier.get(); // Execute to trigger the lambda
                return mockFuture;
            });

            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(app).invokeLater(any());
            projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(Collections.emptyList());

            projectSelectionGroup = spy(new ProjectSelectionGroup(mockIdeProject,
                                                                   mockBranchSelectionGroup,
                                                                   mockScanSelectionGroup,
                                                                   mockResetSelectionAction));
            doNothing().when(projectSelectionGroup).refreshPanel(mockIdeProject);

            Method clear = ProjectSelectionGroup.class.getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(projectSelectionGroup);

            verify(mockPropertiesComponent).setValue(eq(Constants.SELECTED_PROJECT_PROPERTY), isNull());
        }
    }

    @Test
    void override_SetsProjectFromScanAndDelegatesToBranchGroup() throws Exception {
        Application app = mock(Application.class);
        com.checkmarx.ast.project.Project astProject = createAstProject("p1", "project1");
        Scan mockScan = mock(Scan.class);
        when(mockScan.getProjectId()).thenReturn("p1");
        when(mockScan.getBranch()).thenReturn("main");

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd = mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock = mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);

            // Mock ToolWindowManager
            com.intellij.openapi.wm.ToolWindowManager toolWindowManager = mock(com.intellij.openapi.wm.ToolWindowManager.class);
            twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject)).thenReturn(toolWindowManager);

            // Mock CompletableFuture chain
            CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
                consumer.accept(Collections.emptyList());
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
                supplier.get();
                return mockFuture;
            });

            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(app).invokeLater(any());
            projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(Collections.emptyList());

            projectSelectionGroup = spy(new ProjectSelectionGroup(mockIdeProject,
                                                                   mockBranchSelectionGroup,
                                                                   mockScanSelectionGroup,
                                                                   mockResetSelectionAction));
            doNothing().when(projectSelectionGroup).refreshPanel(mockIdeProject);

            // Populate the byId map with the project
            Field byIdField = ProjectSelectionGroup.class.getDeclaredField("byId");
            byIdField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, com.checkmarx.ast.project.Project> byId = (Map<String, com.checkmarx.ast.project.Project>) byIdField.get(projectSelectionGroup);
            byId.put("p1", astProject);

            Method override = ProjectSelectionGroup.class.getDeclaredMethod("override", Scan.class);
            override.setAccessible(true);
            override.invoke(projectSelectionGroup, mockScan);

            // Verify select was called which delegates to branchSelectionGroup
            boolean overrideCalled = mockingDetails(mockBranchSelectionGroup).getInvocations()
                    .stream().anyMatch(inv -> inv.getMethod().getName().equals("override"));
            assertTrue(overrideCalled);
        }
    }

    @Test
    void select_SetsPropertyAndDelegatesToBranchGroup() throws Exception {
        com.checkmarx.ast.project.Project astProject = createAstProject("p3", "name3");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd = mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock = mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);

            // Mock ToolWindowManager
            com.intellij.openapi.wm.ToolWindowManager toolWindowManager = mock(com.intellij.openapi.wm.ToolWindowManager.class);
            twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject)).thenReturn(toolWindowManager);

            // Mock CompletableFuture chain
            CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
                consumer.accept(Collections.emptyList());
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
                supplier.get();
                return mockFuture;
            });

            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(app).invokeLater(any());
            projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(Collections.emptyList());

            projectSelectionGroup = spy(new ProjectSelectionGroup(mockIdeProject,
                                                                   mockBranchSelectionGroup,
                                                                   mockScanSelectionGroup,
                                                                   mockResetSelectionAction));
            doNothing().when(projectSelectionGroup).refreshPanel(mockIdeProject);

            Method select = ProjectSelectionGroup.class.getDeclaredMethod("select", com.checkmarx.ast.project.Project.class);
            select.setAccessible(true);
            select.invoke(projectSelectionGroup, astProject);

            verify(mockPropertiesComponent).setValue(Constants.SELECTED_PROJECT_PROPERTY, "name3");
            boolean clearCalled = mockingDetails(mockBranchSelectionGroup).getInvocations()
                    .stream().anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean refreshCalled = mockingDetails(mockBranchSelectionGroup).getInvocations()
                    .stream().anyMatch(inv -> inv.getMethod().getName().equals("refresh"));
            assertTrue(clearCalled);
            assertTrue(refreshCalled);
        }
    }

    // ===== Helper to build a fully wired ProjectSelectionGroup synchronously =====

    @SuppressWarnings({"rawtypes","unchecked"})
    private ProjectSelectionGroup buildProjectSelectionGroup(
            List<com.checkmarx.ast.project.Project> returnedProjects,
            MockedStatic<PropertiesComponent> propsMock,
            MockedStatic<ApplicationManager> appMock,
            MockedStatic<CompletableFuture> cfMock,
            MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd,
            MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock,
            Application app) throws Exception {

        propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
        appMock.when(ApplicationManager::getApplication).thenReturn(app);
        twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject))
               .thenReturn(mock(com.intellij.openapi.wm.ToolWindowManager.class));

        CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
        when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
            Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
            consumer.accept(returnedProjects);
            return null;
        });
        cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
            Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
            try { supplier.get(); } catch (Exception ignored) {}
            return mockFuture;
        });

        doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(app).invokeLater(any());
        projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(returnedProjects);

        ProjectSelectionGroup group = spy(new ProjectSelectionGroup(
                mockIdeProject, mockBranchSelectionGroup, mockScanSelectionGroup, mockResetSelectionAction));
        doNothing().when(group).refreshPanel(mockIdeProject);
        return group;
    }

    // ===== populate() with non-empty project list =====

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void populate_InheritTrue_StoredNull_MatchingProject_AutoSelectsAndRefreshesBranch() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_INHERIT, "my-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            // storedProject is null → inherit path
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn(null);
            // IDE project name matches → matchProject returns true
            when(mockIdeProject.getName()).thenReturn("my-project");
            utilsMock.when(() -> Utils.getRootRepository(mockIdeProject)).thenReturn(null);

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            // Stored project should be set and branch group refreshed
            verify(mockPropertiesComponent).setValue(Constants.SELECTED_PROJECT_PROPERTY, "my-project");
            verify(mockBranchSelectionGroup).refresh(eq(UUID_INHERIT), eq(true));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void populate_InheritFalse_StoredProjectMatches_RefreshesBranchGroup() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_STORED, "stored-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            // storedProject matches project name
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("stored-project");

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            // Branch group should be refreshed for the stored project, NOT inherit=true
            verify(mockBranchSelectionGroup).refresh(eq(UUID_STORED), eq(false));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void populate_InheritFalse_EnablesBranchAndScanGroups() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn(null);

            // Use refresh() which calls populate(false)
            propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject))
                   .thenReturn(mock(com.intellij.openapi.wm.ToolWindowManager.class));

            CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
                consumer.accept(Collections.emptyList());
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
                try { supplier.get(); } catch (Exception ignored) {}
                return mockFuture;
            });
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(app).invokeLater(any());
            projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(Collections.emptyList());

            projectSelectionGroup = spy(new ProjectSelectionGroup(
                    mockIdeProject, mockBranchSelectionGroup, mockScanSelectionGroup, mockResetSelectionAction));
            doNothing().when(projectSelectionGroup).refreshPanel(mockIdeProject);

            // reset invocations from constructor call
            clearInvocations(mockBranchSelectionGroup, mockScanSelectionGroup, mockResetSelectionAction);

            Method refreshMethod = ProjectSelectionGroup.class.getDeclaredMethod("refresh");
            refreshMethod.setAccessible(true);
            refreshMethod.invoke(projectSelectionGroup);

            // After refresh() → populate(false) → branchSelectionGroup.setEnabled(true) + scanSelectionGroup.setEnabled(true)
            verify(mockBranchSelectionGroup).setEnabled(true);
            verify(mockScanSelectionGroup).setEnabled(true);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void populate_ResetSelectionActionEnabled_WhenProjectListNotEmpty() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_RESET, "any-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("any-project");

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            // resetSelectionAction.setEnabled(true) must be called
            verify(mockResetSelectionAction).setEnabled(true);
        }
    }

    // ===== getTitle() tests =====

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getTitle_WhenNoChildren_Disabled_ReturnsDots() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            projectSelectionGroup = buildProjectSelectionGroup(
                    Collections.emptyList(), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            projectSelectionGroup.setEnabled(false);

            Method getTitle = ProjectSelectionGroup.class.getDeclaredMethod("getTitle");
            getTitle.setAccessible(true);
            String title = (String) getTitle.invoke(projectSelectionGroup);

            assertTrue(title.contains("..."), "Disabled with no children should show dots, got: " + title);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getTitle_WhenNoChildren_Enabled_ReturnsNoneSelected() throws Exception {
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            projectSelectionGroup = buildProjectSelectionGroup(
                    Collections.emptyList(), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            projectSelectionGroup.setEnabled(true);

            Method getTitle = ProjectSelectionGroup.class.getDeclaredMethod("getTitle");
            getTitle.setAccessible(true);
            String title = (String) getTitle.invoke(projectSelectionGroup);

            assertTrue(title.contains("none") || title.toLowerCase().contains("select"),
                    "Enabled with no children should show 'none' or 'select', got: " + title);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getTitle_WhenChildrenPresent_StoredProjectSet_IncludesProjectName() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_TITLE, "alpha-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            // storedProject matches the project → a child Action is added during populate
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn("alpha-project");

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            Method getTitle = ProjectSelectionGroup.class.getDeclaredMethod("getTitle");
            getTitle.setAccessible(true);
            String title = (String) getTitle.invoke(projectSelectionGroup);

            assertTrue(title.contains("alpha-project"),
                    "Title should include stored project name, got: " + title);
        }
    }

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void getTitle_WhenChildrenPresent_StoredProjectBlank_ReturnsNoneSelected() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_BLANK, "beta-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            // storedProject is null → NONE_SELECTED should appear
            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn(null);
            // matchProject short-circuits on name match → no Utils.getRootRepository() call
            when(mockIdeProject.getName()).thenReturn("beta-project");

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            Method getTitle = ProjectSelectionGroup.class.getDeclaredMethod("getTitle");
            getTitle.setAccessible(true);
            String title = (String) getTitle.invoke(projectSelectionGroup);

            assertTrue(title.contains("none") || title.toLowerCase().contains("select"),
                    "Title should show 'none' when no stored project, got: " + title);
        }
    }

    // ===== Inner Action.actionPerformed =====

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void actionInnerClass_ActionPerformed_InvokesSelect() throws Exception {
        com.checkmarx.ast.project.Project p = createAstProject(UUID_ACTION, "action-project");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd =
                     mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock =
                     mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            when(mockPropertiesComponent.getValue(Constants.SELECTED_PROJECT_PROPERTY)).thenReturn(null);
            // matchProject short-circuits on name match → avoids Utils.getRootRepository NPE
            when(mockIdeProject.getName()).thenReturn("action-project");

            projectSelectionGroup = buildProjectSelectionGroup(
                    List.of(p), propsMock, appMock, cfMock, projectCmd, twmMock, app);

            // The Action inner class is added to the group during populate; retrieve it
            com.intellij.openapi.actionSystem.AnAction[] children = projectSelectionGroup.getChildren(null);
            // There should be exactly one Action child for our project
            assertTrue(children.length > 0, "Expected at least one action child");
            com.intellij.openapi.actionSystem.AnActionEvent evt = mock(com.intellij.openapi.actionSystem.AnActionEvent.class);

            clearInvocations(mockPropertiesComponent, mockBranchSelectionGroup);

            children[0].actionPerformed(evt);

            // select() should have been called: sets project property and clears/refreshes branch group
            verify(mockPropertiesComponent).setValue(Constants.SELECTED_PROJECT_PROPERTY, "action-project");
            boolean clearCalled = mockingDetails(mockBranchSelectionGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("clear"));
            boolean refreshCalled = mockingDetails(mockBranchSelectionGroup).getInvocations().stream()
                    .anyMatch(inv -> inv.getMethod().getName().equals("refresh"));
            assertTrue(clearCalled, "branchSelectionGroup.clear() should be called");
            assertTrue(refreshCalled, "branchSelectionGroup.refresh() should be called");
        }
    }

    @Test
    void matchProject_MatchesByNameOrRepositoryUrl() throws Exception {
        com.checkmarx.ast.project.Project astProject = createAstProject("p4", "repo-tail");
        Repository repo = mock(Repository.class);
        when(repo.getPresentableUrl()).thenReturn("https://host/org/repo-tail");
        Application app = mock(Application.class);

        try (MockedStatic<PropertiesComponent> propsMock = mockStatic(PropertiesComponent.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class);
             MockedStatic<com.checkmarx.intellij.ast.commands.Project> projectCmd = mockStatic(com.checkmarx.intellij.ast.commands.Project.class);
             MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<com.intellij.openapi.wm.ToolWindowManager> twmMock = mockStatic(com.intellij.openapi.wm.ToolWindowManager.class)) {

            propsMock.when(() -> PropertiesComponent.getInstance(mockIdeProject)).thenReturn(mockPropertiesComponent);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);

            // Mock ToolWindowManager
            com.intellij.openapi.wm.ToolWindowManager toolWindowManager = mock(com.intellij.openapi.wm.ToolWindowManager.class);
            twmMock.when(() -> com.intellij.openapi.wm.ToolWindowManager.getInstance(mockIdeProject)).thenReturn(toolWindowManager);

            // Mock CompletableFuture chain
            CompletableFuture<List<com.checkmarx.ast.project.Project>> mockFuture = mock(CompletableFuture.class);
            when(mockFuture.thenAccept(any())).thenAnswer(inv -> {
                Consumer<List<com.checkmarx.ast.project.Project>> consumer = inv.getArgument(0);
                consumer.accept(Collections.emptyList());
                return null;
            });
            cfMock.when(() -> CompletableFuture.supplyAsync(any())).thenAnswer(inv -> {
                Supplier<List<com.checkmarx.ast.project.Project>> supplier = inv.getArgument(0);
                supplier.get();
                return mockFuture;
            });

            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(app).invokeLater(any());
            projectCmd.when(com.checkmarx.intellij.ast.commands.Project::getList).thenReturn(Collections.emptyList());

            // Test 1: Names match
            when(mockIdeProject.getName()).thenReturn("repo-tail");
            utilsMock.when(() -> Utils.getRootRepository(mockIdeProject)).thenReturn(null);

            projectSelectionGroup = spy(new ProjectSelectionGroup(mockIdeProject,
                                                                   mockBranchSelectionGroup,
                                                                   mockScanSelectionGroup,
                                                                   null));
            doNothing().when(projectSelectionGroup).refreshPanel(mockIdeProject);

            Method match = ProjectSelectionGroup.class.getDeclaredMethod("matchProject", com.checkmarx.ast.project.Project.class);
            match.setAccessible(true);
            boolean matchesByName = (boolean) match.invoke(projectSelectionGroup, astProject);
            assertTrue(matchesByName, "Should match when IDE project name equals AST project name");

            // Test 2: Repo URL ends with project name
            when(mockIdeProject.getName()).thenReturn("different-name");
            utilsMock.when(() -> Utils.getRootRepository(mockIdeProject)).thenReturn(repo);
            boolean matchesByUrl = (boolean) match.invoke(projectSelectionGroup, astProject);
            assertTrue(matchesByUrl, "Should match when repo URL ends with AST project name");

            // Test 3: No match
            utilsMock.when(() -> Utils.getRootRepository(mockIdeProject)).thenReturn(null);
            boolean noMatch = (boolean) match.invoke(projectSelectionGroup, astProject);
            assertFalse(noMatch, "Should not match when names differ and no repo");
        }
    }
}
