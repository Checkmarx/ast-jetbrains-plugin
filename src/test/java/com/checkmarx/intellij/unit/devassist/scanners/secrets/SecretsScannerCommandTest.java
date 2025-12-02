package com.checkmarx.intellij.unit.devassist.scanners.secrets;

import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerCommand;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SecretsScannerCommandTest {

    private static class TestSecretsScannerCommand extends SecretsScannerCommand {
        public TestSecretsScannerCommand(Disposable parentDisposable, Project project) {
            super(parentDisposable, project);
        }

        @Override
        protected VirtualFile findVirtualFile(String path) {
            return null;
        }

        public void invokeInitializeScanner() {
            super.initializeScanner();
        }
    }

    private Project project;
    private Disposable parentDisposable;
    private SecretsScannerService secretsScannerServiceSpy;
    private TestSecretsScannerCommand command;

    private void invokePrivateScan(SecretsScannerCommand cmd) throws Exception {
        Method method = SecretsScannerCommand.class.getDeclaredMethod("scanAllFilesInProject");
        method.setAccessible(true);
        method.invoke(cmd);
    }

    @BeforeEach
    void setUp() throws Exception {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        parentDisposable = mock(Disposable.class);
        command = new TestSecretsScannerCommand(parentDisposable, project);

        // Spy on internal service field for verification
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        SecretsScannerService originalService = (SecretsScannerService) serviceField.get(command);
        secretsScannerServiceSpy = spy(originalService);
        serviceField.set(command, secretsScannerServiceSpy);
    }

    @Test
    @DisplayName("Constructor initializes internal service and project reference")
    void testConstructor() throws Exception {
        // Verify internal service is initialized
        Field serviceField = SecretsScannerCommand.class.getDeclaredField("secretsScannerService");
        serviceField.setAccessible(true);
        assertNotNull(serviceField.get(command));

        // Verify project reference is set
        Field projectField = SecretsScannerCommand.class.getDeclaredField("project");
        projectField.setAccessible(true);
        assertSame(project, projectField.get(command));
    }

    @Test
    @DisplayName("Initialize scanner completes successfully and logs initialization")
    void testInitializeScanner() {
        // Secrets scanner initialization just logs a message and doesn't throw exceptions
        assertDoesNotThrow(() -> command.invokeInitializeScanner());
    }

    @Test
    @DisplayName("Scan all files with empty content roots performs no scans")
    void testScanAllFilesInProject_EmptyContentRoots() throws Exception {
        // Given
        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[0]);

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When
            invokePrivateScan(command);

            // Then - Should not attempt any scans
            verifyNoInteractions(secretsScannerServiceSpy);
        }
    }

    @Test
    @DisplayName("Scan all files with single non-matching file calls shouldScanFile but performs no actual scanning")
    void testScanAllFilesInProject_NonMatchingFile() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/some/random/file.txt");
        when(root.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        // Mock shouldScanFile to return false for non-matching files
        when(secretsScannerServiceSpy.shouldScanFile(anyString())).thenReturn(false);

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When
            invokePrivateScan(command);

            // Then - Should check if file should be scanned but not perform actual scanning
            verify(secretsScannerServiceSpy).shouldScanFile("/some/random/file.txt");
            verify(secretsScannerServiceSpy, never()).scan(any(), any());
        }
    }

    @Test
    @DisplayName("Scan handles matching secrets file gracefully with overridden findVirtualFile")
    void testScanAllFilesInProject_MatchingFile() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/workspace/config.js");
        when(root.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When & Then - Should handle gracefully due to overridden findVirtualFile
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }

    @Test
    @DisplayName("Scan handles directory traversal with excluded directories")
    void testScanAllFilesInProject_DirectoryTraversal() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        VirtualFile excludedDir = mock(VirtualFile.class);
        VirtualFile validFile = mock(VirtualFile.class);

        when(root.isDirectory()).thenReturn(true);
        when(root.getPath()).thenReturn("/workspace");
        when(root.exists()).thenReturn(true);
        when(root.getChildren()).thenReturn(new VirtualFile[]{excludedDir, validFile});

        // Excluded directory (node_modules)
        when(excludedDir.isDirectory()).thenReturn(true);
        when(excludedDir.getName()).thenReturn("node_modules");
        when(excludedDir.getPath()).thenReturn("/workspace/node_modules");

        // Valid file
        when(validFile.isDirectory()).thenReturn(false);
        when(validFile.getPath()).thenReturn("/workspace/app.js");
        when(validFile.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When & Then - Should process files but skip excluded directories
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }

    @Test
    @DisplayName("Scan handles exception during file processing gracefully")
    void testScanAllFilesInProject_ExceptionHandling() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/workspace/.env");
        when(root.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When & Then - Should handle any exceptions gracefully
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }

    @Test
    @DisplayName("Dispose method handles cleanup gracefully")
    void testDispose() {
        // When & Then - Should not throw exception
        assertDoesNotThrow(() -> command.dispose());
    }

    @Test
    @DisplayName("Scan handles multiple file types correctly")
    void testScanAllFilesInProject_MultipleFileTypes() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        VirtualFile jsFile = mock(VirtualFile.class);
        VirtualFile pyFile = mock(VirtualFile.class);
        VirtualFile envFile = mock(VirtualFile.class);

        when(root.isDirectory()).thenReturn(true);
        when(root.getPath()).thenReturn("/workspace");
        when(root.exists()).thenReturn(true);
        when(root.getChildren()).thenReturn(new VirtualFile[]{jsFile, pyFile, envFile});

        // Setup different file types
        when(jsFile.isDirectory()).thenReturn(false);
        when(jsFile.getPath()).thenReturn("/workspace/app.js");
        when(jsFile.exists()).thenReturn(true);

        when(pyFile.isDirectory()).thenReturn(false);
        when(pyFile.getPath()).thenReturn("/workspace/main.py");
        when(pyFile.exists()).thenReturn(true);

        when(envFile.isDirectory()).thenReturn(false);
        when(envFile.getPath()).thenReturn("/workspace/.env");
        when(envFile.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When & Then - Should process all file types
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }

    @Test
    @DisplayName("Scan handles deeply nested directory structure")
    void testScanAllFilesInProject_NestedDirectories() throws Exception {
        // Given
        VirtualFile root = mock(VirtualFile.class);
        VirtualFile subDir = mock(VirtualFile.class);
        VirtualFile deepFile = mock(VirtualFile.class);

        when(root.isDirectory()).thenReturn(true);
        when(root.getPath()).thenReturn("/workspace");
        when(root.exists()).thenReturn(true);
        when(root.getChildren()).thenReturn(new VirtualFile[]{subDir});

        when(subDir.isDirectory()).thenReturn(true);
        when(subDir.getName()).thenReturn("src");
        when(subDir.getPath()).thenReturn("/workspace/src");
        when(subDir.getChildren()).thenReturn(new VirtualFile[]{deepFile});

        when(deepFile.isDirectory()).thenReturn(false);
        when(deepFile.getPath()).thenReturn("/workspace/src/config.json");
        when(deepFile.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);

            // When & Then - Should handle nested directory traversal
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }
}
