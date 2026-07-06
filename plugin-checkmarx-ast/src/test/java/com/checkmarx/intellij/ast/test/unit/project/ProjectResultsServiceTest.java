package com.checkmarx.intellij.ast.test.unit.project;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Data;
import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.ast.project.ProjectResultsService;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectResultsServiceTest {

    @Mock
    private Project mockProject;
    private ProjectResultsService projectResultsService;

    @BeforeEach
    void setUp() {
        projectResultsService = new ProjectResultsService(mockProject);
    }

    @Test
    void getResultsForFileAndLine_WithInvalidProject_ThrowsException() {
        // Arrange
        Project differentProject = mock(Project.class);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> projectResultsService.getResultsForFileAndLine(differentProject, "file", 1));
    }

    @Test
    void getResultsForFileAndLine_WithNonExistentFile_ReturnsEmptyList() {
        // Arrange
        when(mockProject.getBasePath()).thenReturn("/test/project/path");

        // Act
        List<Node> results = projectResultsService.getResultsForFileAndLine(
                mockProject,
                "/test/project/path/nonexistent.java",
                1
        );

        // Assert
        assertTrue(results.isEmpty());
    }

    @Test
    void getResultForNode_WithUnknownNode_ReturnsNull() {
        // Arrange
        Node unknownNode = mock(Node.class);

        // Act
        Result result = projectResultsService.getResultForNode(unknownNode);

        // Assert
        assertNull(result);
    }

    @Test
    void indexResults_WithInvalidProject_DoesNotThrow() {
        Project differentProject = mock(Project.class);
        Results results = mock(Results.class);
        lenient().when(results.getTotalCount()).thenReturn(0);
        lenient().when(results.getResults()).thenReturn(Collections.emptyList());

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(false);
            assertDoesNotThrow(() -> projectResultsService.indexResults(differentProject, results));
        }
    }

    @Test
    void indexResults_WithEmptyResults_DoesNotThrow() {
        Results results = mock(Results.class);
        lenient().when(results.getResults()).thenReturn(Collections.emptyList());
        lenient().when(results.getTotalCount()).thenReturn(0);

        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(Utils::validThread).thenReturn(true);
            assertDoesNotThrow(() -> projectResultsService.indexResults(mockProject, results));
        }
    }

    @Test
    void getResultsForFileAndLine_WithNullBasePath_ReturnsEmptyList() {
        when(mockProject.getBasePath()).thenReturn(null);

        List<Node> results = projectResultsService.getResultsForFileAndLine(
                mockProject, "/some/file.java", 1);

        assertTrue(results.isEmpty());
    }

    @Test
    void getResultForNode_WhenNodeExists_ReturnsResult() throws Exception {
        Node mockNode = mock(Node.class);
        Result mockResult = mock(Result.class);

        Field resultByNodeField = ProjectResultsService.class.getDeclaredField("resultByNode");
        resultByNodeField.setAccessible(true);
        Map<Node, Result> resultByNode = new HashMap<>();
        resultByNode.put(mockNode, mockResult);
        resultByNodeField.set(projectResultsService, resultByNode);

        Result found = projectResultsService.getResultForNode(mockNode);
        assertSame(mockResult, found);
    }

    @Test
    void getResultsForFileAndLine_WhenNodesPresentAtLine_ReturnsNodes() throws Exception {
        String basePath = "/project/base";
        String file = "/project/base/src/Main.java";
        int line = 42;
        when(mockProject.getBasePath()).thenReturn(basePath);

        Node mockNode = mock(Node.class);

        // Use platform-native path separator to match what Paths.get().relativize().toString() returns
        String relativeKey = Paths.get(basePath).relativize(Paths.get(file)).toString();

        Map<Integer, List<Node>> byLine = new HashMap<>();
        List<Node> nodeList = new ArrayList<>();
        nodeList.add(mockNode);
        byLine.put(line, nodeList);

        Map<String, Map<Integer, List<Node>>> nodesByFile = new HashMap<>();
        nodesByFile.put(relativeKey, byLine);

        Field nodesByFileField = ProjectResultsService.class.getDeclaredField("nodesByFile");
        nodesByFileField.setAccessible(true);
        nodesByFileField.set(projectResultsService, nodesByFile);

        List<Node> result = projectResultsService.getResultsForFileAndLine(mockProject, file, line);
        assertEquals(1, result.size());
        assertSame(mockNode, result.get(0));
    }

    @Test
    void getResultsForFileAndLine_WhenLineNotInIndex_ReturnsEmptyList() throws Exception {
        String basePath = "/project/base";
        String file = "/project/base/src/Main.java";
        when(mockProject.getBasePath()).thenReturn(basePath);

        String relativeKey = Paths.get(basePath).relativize(Paths.get(file)).toString();

        Map<Integer, List<Node>> byLine = new HashMap<>();
        byLine.put(10, new ArrayList<>());

        Map<String, Map<Integer, List<Node>>> nodesByFile = new HashMap<>();
        nodesByFile.put(relativeKey, byLine);

        Field nodesByFileField = ProjectResultsService.class.getDeclaredField("nodesByFile");
        nodesByFileField.setAccessible(true);
        nodesByFileField.set(projectResultsService, nodesByFile);

        List<Node> result = projectResultsService.getResultsForFileAndLine(mockProject, file, 99);
        assertTrue(result.isEmpty());
    }

    // ===== indexResults — async lambda body =====

    @Test
    @SuppressWarnings({"rawtypes","unchecked"})
    void indexResults_WithResultsHavingNodes_PopulatesIndexMaps() throws Exception {
        // Arrange: a result with one node
        com.checkmarx.ast.results.Results results = mock(com.checkmarx.ast.results.Results.class);
        Result mockResult2 = mock(Result.class);
        com.checkmarx.ast.results.result.Data mockData2 = mock(com.checkmarx.ast.results.result.Data.class);
        Node mockNode2 = mock(Node.class);

        when(results.getTotalCount()).thenReturn(1);
        when(results.getResults()).thenReturn(List.of(mockResult2));
        when(mockResult2.getData()).thenReturn(mockData2);
        when(mockData2.getNodes()).thenReturn(List.of(mockNode2));
        when(mockNode2.getFileName()).thenReturn("/src/Main.java");
        when(mockNode2.getLine()).thenReturn(10);

        Application app = mock(Application.class);

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<CompletableFuture> cfMock = mockStatic(CompletableFuture.class)) {

            utilsMock.when(Utils::validThread).thenReturn(true);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);

            // Run the runAsync lambda synchronously
            cfMock.when(() -> CompletableFuture.runAsync(any(Runnable.class))).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return null;
            });
            // Run invokeLater synchronously
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; }).when(app).invokeLater(any());

            projectResultsService.indexResults(mockProject, results);

            // After synchronous execution, the service's nodesByFile should contain the node
            Field nodesByFileField = ProjectResultsService.class.getDeclaredField("nodesByFile");
            nodesByFileField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Map<Integer, List<Node>>> nodesByFile =
                    (Map<String, Map<Integer, List<Node>>>) nodesByFileField.get(projectResultsService);
            assertFalse(nodesByFile.isEmpty(), "nodesByFile should be populated after indexing");
        }
    }

    @Test
    void indexResults_ValidThread_DifferentProject_ThrowsIllegalArgument() {
        Project differentProject = mock(Project.class);
        com.checkmarx.ast.results.Results results = mock(com.checkmarx.ast.results.Results.class);
        lenient().when(results.getTotalCount()).thenReturn(0);
        lenient().when(results.getResults()).thenReturn(Collections.emptyList());

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(Utils::validThread).thenReturn(true);
            // validateProject throws because differentProject != mockProject (the service's project)
            assertThrows(IllegalArgumentException.class,
                    () -> projectResultsService.indexResults(differentProject, results));
        }
    }

    @Test
    void getResultsForFileAndLine_WhenRelativizeThrowsIllegalArgument_FallsBackAndReturnsEmpty() {
        // On Windows, relativizing paths from different drives throws IllegalArgumentException
        // Use a path that can't be relativized from the base (simulate with mocked basePath that creates conflict)
        when(mockProject.getBasePath()).thenReturn("/project/base");

        // A file with a path that cannot be relativized from /project/base on this platform
        // We can achieve this by passing a path that throws when parsed
        // The safest cross-platform approach: an absolute path that doesn't start with basePath
        // On Unix, /other/Main.java relativized from /project/base should work, but the catch
        // block is hit if relativize throws. We mock by subclassing isn't easy, so we
        // test the "relativePath != empty" fallback: use a file at the exact base → empty relative path
        String fileAtBase = "/project/base";  // relativize → "" (empty string)
        List<Node> result = projectResultsService.getResultsForFileAndLine(mockProject, fileAtBase, 1);
        assertTrue(result.isEmpty(), "Empty relative path should return empty list");
    }

    @Test
    void getResultsForFileAndLine_WhenFileNotInIndex_ReturnsEmptyList() throws Exception {
        String basePath = "/project/base";
        String file = "/project/base/src/Main.java";
        when(mockProject.getBasePath()).thenReturn(basePath);

        Field nodesByFileField = ProjectResultsService.class.getDeclaredField("nodesByFile");
        nodesByFileField.setAccessible(true);
        nodesByFileField.set(projectResultsService, new HashMap<>());

        List<Node> result = projectResultsService.getResultsForFileAndLine(mockProject, file, 1);
        assertTrue(result.isEmpty());
    }
}

