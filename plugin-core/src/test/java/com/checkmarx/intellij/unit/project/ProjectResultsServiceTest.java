package com.checkmarx.intellij.unit.project;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.project.ProjectResultsService;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
} 