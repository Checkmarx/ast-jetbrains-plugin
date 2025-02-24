package com.checkmarx.intellij.unit.ASCA;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.asca.Error;
import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.commands.ASCA;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AscaServiceTest {

    @Mock
    private Logger mockLogger;

    @Mock
    private Project mockProject;

    @Mock
    private PsiFile mockPsiFile;

    @Mock
    private VirtualFile mockVirtualFile;

    @Mock
    private Document mockDocument;

    @Mock
    private PsiDocumentManager mockPsiDocumentManager;

    @Mock
    private Application mockApplication;

    @Mock
    private ScanResult mockScanResult;

    private AscaService ascaService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ascaService = new AscaService(mockLogger);
    }

    @Test
    void runAscaScan_WithNullFile_ReturnsNull() {
        // Act
        ScanResult result = ascaService.runAscaScan(null, mockProject, true, "test-agent");

        // Assert
        assertNull(result);
        verify(mockLogger, never()).warn(anyString());
    }

    @Test
    void runAscaScan_WithNonLocalFile_ReturnsNull() {
        // Arrange
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(false);

        // Act
        ScanResult result = ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

        // Assert
        assertNull(result);
        verify(mockLogger, never()).warn(anyString());
    }

    @Test
    void runAscaScan_Success() throws Exception {
        // Arrange
        String testContent = "test content";
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable)).thenReturn(testContent);

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(mockScanResult);

            // Act
            ScanResult result = ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            assertNotNull(result);
            assertEquals(mockScanResult, result);
            verify(mockLogger).info(contains("Starting ASCA scan on file:"));
        }
    }

    @Test
    void runAscaScan_WithScanError_LogsWarning() throws Exception {
        // Arrange
        String testContent = "test content";
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable2 = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable2)).thenReturn(testContent);

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenThrow(mock(CxException.class));

            // Act
            ScanResult result = ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            assertNull(result);
            verify(mockLogger).warn(eq("Error during ASCA scan:"), any(CxException.class));
        }
    }

    @Test
    void runAscaScan_WithNoDocument_UsesVirtualFile() throws Exception {
        // Arrange
        String testContent = "test content";
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");
        when(mockVirtualFile.contentsToByteArray()).thenReturn(testContent.getBytes());

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);
            when(mockPsiDocumentManager.getDocument(mockPsiFile)).thenReturn(null);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable3 = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable3)).thenAnswer(inv -> {
                Computable<String> computable = inv.getArgument(0);
                return computable.compute();
            });

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(mockScanResult);

            // Act
            ScanResult result = ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            assertNotNull(result);
            assertEquals(mockScanResult, result);
            verify(mockVirtualFile).contentsToByteArray();
        }
    }

    @Test
    void installAsca_Success() throws Exception {
        // Arrange
        when(mockScanResult.getError()).thenReturn(null);
        try (MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {
            ascaMock.when(ASCA::installAsca).thenReturn(mockScanResult);

            // Act
            boolean result = ascaService.installAsca();

            // Assert
            assertTrue(result);
            verify(mockLogger, never()).warn(anyString());
        }
    }

    @Test
    void installAsca_WithError_ReturnsFalse() throws Exception {
        // Arrange
        Error error = mock(Error.class);
        when(error.getDescription()).thenReturn("Installation failed");
        when(mockScanResult.getError()).thenReturn(error);

        try (MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {
            ascaMock.when(ASCA::installAsca).thenReturn(mockScanResult);

            // Act
            boolean result = ascaService.installAsca();

            // Assert
            assertFalse(result);
            verify(mockLogger).warn(contains("ASCA installation error:"));
        }
    }

    @Test
    void handleScanResult_WithError_LogsWarning() throws Exception {
        // Arrange
        Error error = mock(Error.class);
        when(error.getDescription()).thenReturn("Test error description");
        when(mockScanResult.getError()).thenReturn(error);
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable)).thenReturn("test content");

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(mockScanResult);

            // Act
            ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            verify(mockLogger).warn(eq("ASCA scan error: Test error description"));
        }
    }

    @Test
    void handleScanResult_WithViolations_LogsInfo() throws Exception {
        // Arrange
        when(mockScanResult.getError()).thenReturn(null);
        when(mockScanResult.getScanDetails()).thenReturn(Collections.singletonList(mock(ScanDetail.class)));
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable)).thenReturn("test content");

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(mockScanResult);

            // Act
            ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            verify(mockLogger).info(eq("1 security best practice violation found in test.java in test.java"));
        }
    }

    @Test
    void handleScanResult_WithMultipleViolations_LogsInfo() throws Exception {
        // Arrange
        when(mockScanResult.getError()).thenReturn(null);
        when(mockScanResult.getScanDetails()).thenReturn(Arrays.asList(
            mock(ScanDetail.class),
            mock(ScanDetail.class)
        ));
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable)).thenReturn("test content");

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(mockScanResult);

            // Act
            ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            verify(mockLogger).info(eq("2 security best practice violations found intest.java in test.java"));
        }
    }

    @Test
    void handleScanResult_WithNullScanResult_LogsWarning() throws Exception {
        // Arrange
        when(mockPsiFile.getName()).thenReturn("test.java");
        when(mockPsiFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.isInLocalFileSystem()).thenReturn(true);
        when(mockVirtualFile.getPath()).thenReturn("/test/path/test.java");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class);
             MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class);
             MockedStatic<ASCA> ascaMock = mockStatic(ASCA.class)) {

            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);

            appManagerMock.when(ApplicationManager::getApplication)
                    .thenReturn(mockApplication);
            @SuppressWarnings("unchecked")
            Computable<String> anyComputable = any(Computable.class);
            when(mockApplication.runReadAction(anyComputable)).thenReturn("test content");

            ascaMock.when(() -> ASCA.scanAsca(anyString(), eq(true), eq("test-agent")))
                    .thenReturn(null);

            // Act
            ascaService.runAscaScan(mockPsiFile, mockProject, true, "test-agent");

            // Assert
            verify(mockLogger).warn(eq("ASCA scan error: Unknown error"));
        }
    }
}