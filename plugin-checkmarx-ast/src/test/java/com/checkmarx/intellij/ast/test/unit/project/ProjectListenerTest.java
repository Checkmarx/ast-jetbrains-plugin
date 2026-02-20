package com.checkmarx.intellij.ast.test.unit.project;

import com.checkmarx.intellij.ast.commands.Results;
import com.checkmarx.intellij.ast.project.ProjectListener;
import com.checkmarx.intellij.ast.project.ProjectResultsService;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectListenerTest {

    @Mock
    private Project mockProject;

    @Mock
    private ProjectResultsService mockProjectResultsService;

    @Test
    void projectOpened_InitializesProjectResultsService() {
        // Arrange
        ProjectListener projectListener = new ProjectListener();
        when(mockProject.getService(ProjectResultsService.class)).thenReturn(mockProjectResultsService);

        // Act
        projectListener.projectOpened(mockProject);

        // Assert
        verify(mockProjectResultsService).indexResults(mockProject, Results.emptyResults);
    }
} 