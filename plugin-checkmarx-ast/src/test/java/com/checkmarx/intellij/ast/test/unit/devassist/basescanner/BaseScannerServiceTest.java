package com.checkmarx.intellij.ast.test.unit.devassist.basescanner;

import com.checkmarx.intellij.devassist.basescanner.BaseScannerService;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseScannerServiceTest {
    private ScannerConfig config;
    private BaseScannerService<String> service;

    static class TestableBaseScannerService extends BaseScannerService<String> {
        public TestableBaseScannerService(ScannerConfig config) {
            super(config);
        }
        public String getTempSubFolderPathPublic(String baseDir) {
            return super.getTempSubFolderPath(baseDir);
        }
        public void createTempFolderPublic(Path folderPath) {
            super.createTempFolder(folderPath);
        }
        public void deleteTempFolderPublic(Path tempFolder) {
            super.deleteTempFolder(tempFolder);
        }
    }

    private TestableBaseScannerService testableService;

    @BeforeEach
    void setUp() {
        config = mock(ScannerConfig.class);
        service = new BaseScannerService<>(config);
        testableService = new TestableBaseScannerService(config);
    }

    @Test
    @DisplayName("constructor sets config correctly")
    void testConstructor_setsConfig() {
        assertEquals(config, service.getConfig());
    }

    @Test
    @DisplayName("shouldScanFile skips node_modules and scans valid files")
    void testShouldScanFile_skipsNodeModulesAndScansValidFiles() {
        PsiFile psiFile = mock(PsiFile.class);
        assertFalse(service.shouldScanFile("/foo/node_modules/bar.js",psiFile));
        assertTrue(service.shouldScanFile("/foo/src/bar.js",psiFile));
        assertTrue(service.shouldScanFile("bar.js",psiFile));
    }

    @Test
    @DisplayName("scan returns null for PsiFile")
    void testScan_returnsNullForPsiFile() {
        PsiFile psiFile = mock(PsiFile.class);
        assertNull(service.scan(psiFile, "uri"));
    }

    @Test
    @DisplayName("getTempSubFolderPath returns temp path containing baseDir and tmpdir")
    void testGetTempSubFolderPath_returnsTempPathContainingBaseDirAndTmpdir() {
        String baseDir = "myTempDir";
        String tempPath = testableService.getTempSubFolderPathPublic(baseDir);
        assertTrue(tempPath.contains(baseDir));
        assertTrue(tempPath.contains(System.getProperty("java.io.tmpdir")));
    }

    @Test
    @DisplayName("createTempFolder creates directory successfully")
    void testCreateTempFolder_createsDirectorySuccessfully() throws IOException {
        Path tempDir = Files.createTempDirectory("cxTestCreate");
        Path subDir = tempDir.resolve("subdir");
        try {
            testableService.createTempFolderPublic(subDir);
            assertTrue(Files.exists(subDir));
        } finally {
            Files.deleteIfExists(subDir);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @DisplayName("createTempFolder handles IOException gracefully")
    void testCreateTempFolder_handlesIOExceptionGracefully() throws IOException {
        Path tempDir = mock(Path.class);
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.createDirectories(tempDir)).thenThrow(new IOException("fail"));
            testableService.createTempFolderPublic(tempDir); // Should not throw
        }
    }

    @Test
    @DisplayName("deleteTempFolder deletes files and folders successfully")
    void testDeleteTempFolder_deletesFilesAndFoldersSuccessfully() throws IOException {
        Path tempDir = Files.createTempDirectory("cxTestDelete");
        Path file = tempDir.resolve("file.txt");
        Files.createFile(file);
        testableService.deleteTempFolderPublic(tempDir);
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(tempDir));
    }

    @Test
    @DisplayName("deleteTempFolder handles IOException on walk gracefully")
    void testDeleteTempFolder_handlesIOExceptionOnWalkGracefully() throws IOException {
        Path tempDir = Files.createTempDirectory("cxTestDeleteWalk");
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.notExists(tempDir)).thenReturn(false);
            filesMock.when(() -> Files.walk(tempDir)).thenThrow(new IOException("fail walk"));
            testableService.deleteTempFolderPublic(tempDir); // Should not throw
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    @DisplayName("deleteTempFolder handles exception on delete gracefully")
    void testDeleteTempFolder_handlesExceptionOnDeleteGracefully() throws IOException {
        Path tempDir = Files.createTempDirectory("cxTestDeleteEx");
        Path file = tempDir.resolve("file.txt");
        Files.createFile(file);
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.notExists(tempDir)).thenReturn(false);
            filesMock.when(() -> Files.walk(tempDir)).thenReturn(Stream.of(file, tempDir));
            filesMock.when(() -> Files.deleteIfExists(file)).thenThrow(new RuntimeException("fail delete"));
            filesMock.when(() -> Files.deleteIfExists(tempDir)).thenReturn(true);
            testableService.deleteTempFolderPublic(tempDir); // Should not throw
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(tempDir);
        }
    }
}
