package com.checkmarx.intellij.ast.test.unit.commands;

import com.checkmarx.ast.project.Project;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectTest {

    @Mock
    private CxWrapper mockWrapper;

    @BeforeEach
    void setUp() {
    }

    @Test
    void getList_Success() throws CxException, IOException, InterruptedException {
        // Arrange
        List<Project> expectedProjects = Arrays.asList(mock(Project.class), mock(Project.class));
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.projectList("limit=10000")).thenReturn(expectedProjects);

            // Act
            List<Project> result = com.checkmarx.intellij.commands.Project.getList();

            // Assert
            assertNotNull(result);
            assertEquals(expectedProjects, result);
            verify(mockWrapper).projectList("limit=10000");
        }
    }

    @Test
    void getList_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.projectList(anyString())).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                com.checkmarx.intellij.commands.Project.getList()
            );
        }
    }

    @Test
    void getBranches_Success_NonSCMProject() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        List<String> expectedBranches = Arrays.asList("main", "develop");
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.projectBranches(eq(projectId), eq(""))).thenReturn(expectedBranches);

            // Act
            List<String> result = com.checkmarx.intellij.commands.Project.getBranches(projectId, false);

            // Assert
            assertNotNull(result);
            assertEquals(expectedBranches, result);
            verify(mockWrapper).projectBranches(projectId, "");
        }
    }

    @Test
    void getBranches_Success_SCMProject() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        List<String> branches = new ArrayList<>(Arrays.asList("main", "develop"));
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.projectBranches(eq(projectId), eq(""))).thenReturn(new ArrayList<>(branches));

            // Act
            List<String> result = com.checkmarx.intellij.commands.Project.getBranches(projectId, true);

            // Assert
            assertNotNull(result);
            assertEquals(Constants.USE_LOCAL_BRANCH, result.get(0));
            assertEquals(branches.size() + 1, result.size());
            assertTrue(result.containsAll(branches));
            verify(mockWrapper).projectBranches(projectId, "");
        }
    }

    @Test
    void getBranches_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        UUID projectId = UUID.randomUUID();
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.projectBranches(any(UUID.class), anyString())).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                com.checkmarx.intellij.commands.Project.getBranches(projectId, false)
            );
        }
    }
} 