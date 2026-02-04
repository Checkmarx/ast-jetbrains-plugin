package com.checkmarx.intellij.ast.test.unit.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.scanners.iac.IacScannerService;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IacScannerServiceTest {

    private static class TestableIacScannerService extends IacScannerService {
        private final Path tempRoot;
        TestableIacScannerService(Path tempRoot) {
            this.tempRoot = tempRoot;
        }

        @Override
        protected String getTempSubFolderPath(String baseDir) {
            return tempRoot.resolve(baseDir).toString();
        }
    }

    private IacScannerService service;
    private PsiFile psiFile;
    private VirtualFile virtualFile;

    @BeforeEach
    void setUp() {
        service = new IacScannerService();
        psiFile = mock(PsiFile.class);
        virtualFile = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(psiFile.getName()).thenReturn("main.tf");
        when(virtualFile.getPath()).thenReturn("/project/main.tf");
    }

    private String readFileType(IacScannerService target) throws Exception {
        Field field = IacScannerService.class.getDeclaredField("fileType");
        field.setAccessible(true);
        return (String) field.get(target);
    }

    @Test
    @DisplayName("shouldScanFile returns false for node_modules paths")
    void shouldScanFileRejectsNodeModules() {
        assertFalse(service.shouldScanFile("/repo/node_modules/main.tf", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile sets docker file type when Dockerfile matches pattern")
    void shouldScanFileDetectsDockerfile() throws Exception {
        assertTrue(service.shouldScanFile("/repo/Dockerfile", psiFile));
        assertEquals(DevAssistConstants.DOCKERFILE, readFileType(service));
    }

    @Test
    @DisplayName("shouldScanFile uses PsiFile extension when tfvars pattern matches")
    void shouldScanFileUsesExtensionForPattern() throws Exception {
        when(virtualFile.getExtension()).thenReturn("tfvars");
        assertTrue(service.shouldScanFile("/repo/env/config.auto.tfvars", psiFile));
        assertEquals("tfvars", readFileType(service));
    }

    @Test
    @DisplayName("shouldScanFile returns true for supported extensions outside patterns")
    void shouldScanFileSupportsExtensionList() throws Exception {
        when(virtualFile.exists()).thenReturn(true);
        when(virtualFile.getExtension()).thenReturn("yaml");
        assertTrue(service.shouldScanFile("/repo/chart/values.yaml", psiFile));
        assertEquals("yaml", readFileType(service));
    }

    @Test
    @DisplayName("shouldScanFile returns false when virtual file is missing")
    void shouldScanFileFailsWhenVirtualFileMissing() {
        when(virtualFile.exists()).thenReturn(false);
        assertFalse(service.shouldScanFile("/repo/chart/values.yaml", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile returns false when extension is unsupported")
    void shouldScanFileRejectsUnsupportedExtensions() {
        when(virtualFile.exists()).thenReturn(true);
        when(virtualFile.getExtension()).thenReturn("txt");
        assertFalse(service.shouldScanFile("/repo/chart/notes.txt", psiFile));
    }

    @Test
    @DisplayName("shouldScanFile returns false when extension is missing")
    void shouldScanFileRejectsWhenExtensionMissing() {
        when(virtualFile.exists()).thenReturn(true);
        when(virtualFile.getExtension()).thenReturn(null);
        assertFalse(service.shouldScanFile("/repo/chart/values", psiFile));
    }

    @Test
    @DisplayName("scan returns null when shouldScanFile declines the file")
    void scanReturnsNullWhenShouldScanFileFalse() {
        IacScannerService spyService = spy(new IacScannerService());
        doReturn(false).when(spyService).shouldScanFile("/repo/main.tf", psiFile);
        assertNull(spyService.scan(psiFile, "/repo/main.tf"));
    }

    @Test
    @DisplayName("scan stops when the underlying virtual file no longer exists")
    void scanReturnsNullWhenVirtualFileMissing() {
        IacScannerService spyService = spy(new IacScannerService());
        when(virtualFile.exists()).thenReturn(false);
        doReturn(true).when(spyService).shouldScanFile("/repo/main.tf", psiFile);
        assertNull(spyService.scan(psiFile, "/repo/main.tf"));
        verify(virtualFile).exists();
    }

    @Test
    @DisplayName("scan returns null when file content is blank")
    void scanReturnsNullWhenContentBlank() {
        IacScannerService spyService = spy(new IacScannerService());
        when(virtualFile.exists()).thenReturn(true);
        doReturn(true).when(spyService).shouldScanFile("/repo/main.tf", psiFile);

        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.getFileContent(psiFile)).thenReturn("   ");
            assertNull(spyService.scan(psiFile, "/repo/main.tf"));
        }
    }

    @Test
    @DisplayName("scan writes content and returns adaptor enriched with file type information")
    void scanSuccessReturnsIssues() throws Exception {
        when(virtualFile.exists()).thenReturn(true);
        when(virtualFile.getExtension()).thenReturn("tf");
        when(virtualFile.getPath()).thenReturn("/repo/main.tf");
        when(psiFile.getProject()).thenReturn(mock(com.intellij.openapi.project.Project.class));

        Path tempDir = Files.createTempDirectory("iac-scan-test");
        IacScannerService testService = new TestableIacScannerService(tempDir);

        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class);
             MockedStatic<TelemetryService> telemetry = mockStatic(TelemetryService.class);
             MockedConstruction<IgnoreManager> ignoreMgr = mockConstruction(IgnoreManager.class, (mock, context) -> {
                 when(mock.hasIgnoredEntries(any())).thenReturn(false);
             })) {

            utils.when(() -> DevAssistUtils.getFileContent(psiFile)).thenReturn("resource");
            utils.when(DevAssistUtils::getContainerTool).thenReturn("docker");
            utils.when(() -> DevAssistUtils.getFileExtension(psiFile)).thenReturn("tf");
            utils.when(() -> DevAssistUtils.getIgnoreFilePath(any())).thenReturn("");
            telemetry.when(() -> TelemetryService.logScanResults(any(ScanResult.class), any(ScanEngine.class))).then(invocation -> null);

            CxWrapper wrapper = mock(CxWrapper.class);
            IacRealtimeResults results = mock(IacRealtimeResults.class);
            IacRealtimeResults.Issue issue = mock(IacRealtimeResults.Issue.class);
            RealtimeLocation location = mock(RealtimeLocation.class);

            when(location.getLine()).thenReturn(2);
            when(location.getStartIndex()).thenReturn(1);
            when(location.getEndIndex()).thenReturn(4);

            when(issue.getTitle()).thenReturn("Terraform finding");
            when(issue.getDescription()).thenReturn("desc");
            when(issue.getSeverity()).thenReturn("HIGH");
            when(issue.getFilePath()).thenReturn("/repo/main.tf");
            when(issue.getLocations()).thenReturn(List.of(location));
            when(issue.getSimilarityId()).thenReturn("sim");
            when(issue.getActualValue()).thenReturn("actual");
            when(issue.getExpectedValue()).thenReturn("expected");

            when(results.getResults()).thenReturn(List.of(issue));
            when(wrapper.iacRealtimeScan(anyString(), anyString(), anyString())).thenReturn(results);
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);

            ScanResult<IacRealtimeResults> result = testService.scan(psiFile, "/repo/main.tf");

            assertNotNull(result);
            assertEquals(1, result.getIssues().size());
            assertEquals("tf", result.getIssues().get(0).getFileType());
            assertEquals(ScanEngine.IAC, result.getIssues().get(0).getScanEngine());
            assertEquals(3, result.getIssues().get(0).getLocations().get(0).getLine());
        } finally {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    @DisplayName("createConfig sets expected engine name and messages")
    void createConfigHasIacEngineName() {
        var config = IacScannerService.createConfig();
        assertEquals(ScanEngine.IAC.name(), config.getEngineName());
        assertEquals(DevAssistConstants.IAC_REALTIME_SCANNER, config.getConfigSection());
        assertEquals(DevAssistConstants.ACTIVATE_IAC_REALTIME_SCANNER, config.getActivateKey());
    }
}


