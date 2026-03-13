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

    @BeforeEach
    void setUp() {
        lenient().when(mockIdeProject.getService(PropertiesComponent.class)).thenReturn(mockPropertiesComponent);
    }

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
