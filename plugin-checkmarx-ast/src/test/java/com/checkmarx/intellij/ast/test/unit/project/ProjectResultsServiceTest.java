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

import java.util.Collections;
import java.util.List;

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
}

