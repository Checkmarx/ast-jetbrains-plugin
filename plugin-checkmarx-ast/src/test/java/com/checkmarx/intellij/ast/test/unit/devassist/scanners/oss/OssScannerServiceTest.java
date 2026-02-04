package com.checkmarx.intellij.ast.test.unit.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.ossrealtime.OssRealtimeVulnerability;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.devassist.scanners.oss.OssScanResultAdaptor;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OssScannerServiceTest {
    private static class TestableOssScannerService extends OssScannerService {
        private final Path overrideTemp;
        TestableOssScannerService(Path overrideTemp){ this.overrideTemp = overrideTemp; }
        @Override
        protected Path getTempSubFolderPath(@SuppressWarnings("NullableProblems") PsiFile file) {
            return overrideTemp;
        }
    }
    private static class NoDeleteOssScannerService extends OssScannerService {
        private final Path overrideTemp;
        NoDeleteOssScannerService(Path overrideTemp){ this.overrideTemp = overrideTemp; }
        @Override
        protected Path getTempSubFolderPath(@SuppressWarnings("NullableProblems") PsiFile file) {
            return overrideTemp;
        }
        @Override
        protected void deleteTempFolder(@NotNull Path tempFolder) { /* no-op for test visibility */ }
    }

    private PsiFile mockPsiFile(String name) {
        PsiFile psi = mock(PsiFile.class, RETURNS_DEEP_STUBS);
        when(psi.getName()).thenReturn(name);
        return psi;
    }

    @Test @DisplayName("shouldScanFile_nonManifestPath_returnsFalse")
    void testShouldScanFile_nonManifestPath_returnsFalse() {
        PsiFile psi = mockPsiFile("README.md");
        assertFalse(new OssScannerService().shouldScanFile("/project/README.md",psi));
    }
    @Test @DisplayName("shouldScanFile_nodeModulesPath_returnsFalse")
    void testShouldScanFile_nodeModulesPath_returnsFalse() {
        PsiFile psi = mockPsiFile("package.json");
        assertFalse(new OssScannerService().shouldScanFile("/project/node_modules/package.json",psi));
    }
    @Test @DisplayName("shouldScanFile_manifestPath_returnsTrue")
    void testShouldScanFile_manifestPath_returnsTrue() {
        PsiFile psi = mockPsiFile("package.json");
        assertTrue(new OssScannerService().shouldScanFile("/project/package.json",psi));
    }

    @Test @DisplayName("scan_shouldScanFileFalse_returnsNull")
    void testScan_shouldScanFileFalse_returnsNull() {
        OssScannerService service = spy(new OssScannerService());
        PsiFile psi = mockPsiFile("package.json");
        doReturn(false).when(service).shouldScanFile("package.json",psi);
        assertNull(service.scan(psi, "package.json"));
    }

    @Test @DisplayName("scan_blankContent_returnsNull")
    void testScan_blankContent_returnsNull() {
        OssScannerService service = spy(new OssScannerService());
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("package.json",psi);
        try(MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn("   ");
            assertNull(service.scan(psi, "/project/package.json"));
        }
    }

    @Test @DisplayName("scan_nullContent_returnsNull")
    void testScan_nullContent_returnsNull() {
        OssScannerService service = spy(new OssScannerService());
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("/project/package.json",psi);
        try(MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class)) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn(null);
            assertNull(service.scan(psi, "/project/package.json"));
        }
    }

    @Test @DisplayName("scan_validContent_noPackages_returnsAdaptorWithEmptyIssues")
    void testScan_validContent_noPackages_returnsAdaptorWithEmptyIssues() throws Exception {
        Path temp = Files.createTempDirectory("oss-no-packages");
        OssScannerService service = spy(new TestableOssScannerService(temp));
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("package.json",psi);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class);
             MockedStatic<com.checkmarx.intellij.devassist.telemetry.TelemetryService> telemetry = mockStatic(com.checkmarx.intellij.devassist.telemetry.TelemetryService.class);
             MockedConstruction<com.checkmarx.intellij.devassist.ignore.IgnoreManager> ignoreMgrConstruction = mockConstruction(com.checkmarx.intellij.devassist.ignore.IgnoreManager.class, (mock, context) -> {
                 when(mock.hasIgnoredEntries(any())).thenReturn(false);
             })) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn("{ }\n");
            utils.when(() -> DevAssistUtils.getIgnoreFilePath(any(com.intellij.openapi.project.Project.class))).thenReturn("");
            telemetry.when(() -> com.checkmarx.intellij.devassist.telemetry.TelemetryService.logScanResults(any(com.checkmarx.intellij.devassist.common.ScanResult.class), any(ScanEngine.class))).then(invocation -> null);
            CxWrapper wrapper = mock(CxWrapper.class);
            OssRealtimeResults realtimeResults = mock(OssRealtimeResults.class);
            when(realtimeResults.getPackages()).thenReturn(List.of());
            when(wrapper.ossRealtimeScan(anyString(), anyString())).thenReturn(realtimeResults);
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);
            com.checkmarx.intellij.devassist.common.ScanResult<?> result = service.scan(psi, temp.resolve("package.json").toString());
            assertNotNull(result);
            assertTrue(result.getIssues().isEmpty());
        }
    }

    @Test @DisplayName("scan_validContent_withIssues_mapsVulnsAndLocations")
    void testScan_validContent_withIssues_mapsVulnsAndLocations() throws Exception {
        Path temp = Files.createTempDirectory("oss-with-packages");
        OssScannerService service = spy(new TestableOssScannerService(temp));
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("package.json",psi);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class);
             MockedStatic<com.checkmarx.intellij.devassist.telemetry.TelemetryService> telemetry = mockStatic(com.checkmarx.intellij.devassist.telemetry.TelemetryService.class);
             MockedConstruction<com.checkmarx.intellij.devassist.ignore.IgnoreManager> ignoreMgrConstruction = mockConstruction(com.checkmarx.intellij.devassist.ignore.IgnoreManager.class, (mock, context) -> {
                 when(mock.hasIgnoredEntries(any())).thenReturn(false);
             })) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn("{ }\n");
            utils.when(() -> DevAssistUtils.getIgnoreFilePath(any(com.intellij.openapi.project.Project.class))).thenReturn("");
            telemetry.when(() -> com.checkmarx.intellij.devassist.telemetry.TelemetryService.logScanResults(any(com.checkmarx.intellij.devassist.common.ScanResult.class), any(ScanEngine.class))).then(invocation -> null);
            CxWrapper wrapper = mock(CxWrapper.class);
            OssRealtimeResults realtimeResults = mock(OssRealtimeResults.class);
            OssRealtimeScanPackage pkg = mock(OssRealtimeScanPackage.class);
            OssRealtimeVulnerability vul = mock(OssRealtimeVulnerability.class);
            RealtimeLocation loc = mock(RealtimeLocation.class);
            when(loc.getLine()).thenReturn(4); // zero-based line 4 -> stored +1 in Location
            when(loc.getStartIndex()).thenReturn(1);
            when(loc.getEndIndex()).thenReturn(3);
            when(vul.getCve()).thenReturn("CVE-123");
            when(vul.getDescription()).thenReturn("Desc");
            when(vul.getSeverity()).thenReturn("HIGH");
            when(vul.getFixVersion()).thenReturn("2.0.0");
            when(pkg.getPackageName()).thenReturn("mypkg");
            when(pkg.getPackageVersion()).thenReturn("1.0.0");
            when(pkg.getStatus()).thenReturn("CRITICAL");
            when(pkg.getVulnerabilities()).thenReturn(List.of(vul));
            when(pkg.getLocations()).thenReturn(List.of(loc));
            when(realtimeResults.getPackages()).thenReturn(List.of(pkg));
            when(wrapper.ossRealtimeScan(anyString(), anyString())).thenReturn(realtimeResults);
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);
            var result = service.scan(psi, temp.resolve("package.json").toString());
            assertNotNull(result);
            assertEquals(1, result.getIssues().size());
            var issue = result.getIssues().get(0);
            assertEquals("mypkg", issue.getTitle());
            assertEquals("1.0.0", issue.getPackageVersion());
            assertEquals(ScanEngine.OSS, issue.getScanEngine());
            assertEquals("CRITICAL", issue.getSeverity());
            assertEquals(1, issue.getVulnerabilities().size());
            assertEquals("CVE-123", issue.getVulnerabilities().get(0).getCve());
            assertEquals(1, issue.getLocations().size());
            assertEquals(5, issue.getLocations().get(0).getLine()); // +1 applied
        }
    }
    @Test @DisplayName("scan_validContent_withCompanionFile_copiesLockFile")
    void testScan_validContent_withCompanionFile_copiesLockFile() throws Exception {
        Path parent = Files.createTempDirectory("oss-companion");
        // create companion source file
        Files.writeString(parent.resolve("package-lock.json"), "lock");
        Path forcedTemp = Files.createTempDirectory("oss-companion-target");
        OssScannerService service = spy(new NoDeleteOssScannerService(forcedTemp));
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("package.json",psi);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class);
             MockedStatic<com.checkmarx.intellij.devassist.telemetry.TelemetryService> telemetry = mockStatic(com.checkmarx.intellij.devassist.telemetry.TelemetryService.class);
             MockedConstruction<com.checkmarx.intellij.devassist.ignore.IgnoreManager> ignoreMgrConstruction = mockConstruction(com.checkmarx.intellij.devassist.ignore.IgnoreManager.class, (mock, context) -> {
                 when(mock.hasIgnoredEntries(any())).thenReturn(false);
             })) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn("{ }\n");
            utils.when(() -> DevAssistUtils.getIgnoreFilePath(any(com.intellij.openapi.project.Project.class))).thenReturn("");
            telemetry.when(() -> com.checkmarx.intellij.devassist.telemetry.TelemetryService.logScanResults(any(com.checkmarx.intellij.devassist.common.ScanResult.class), any(ScanEngine.class))).then(invocation -> null);
            CxWrapper wrapper = mock(CxWrapper.class);
            OssRealtimeResults realtimeResults = mock(OssRealtimeResults.class);
            when(realtimeResults.getPackages()).thenReturn(List.of());
            when(wrapper.ossRealtimeScan(anyString(), anyString())).thenReturn(realtimeResults);
            factory.when(CxWrapperFactory::build).thenReturn(wrapper);
            service.scan(psi, parent.resolve("package.json").toString());
            assertTrue(Files.exists(forcedTemp.resolve("package-lock.json")), "Companion lock file should be copied to temp folder");
        }
    }

    @Test @DisplayName("scan_wrapperThrowsIOException_returnsNull")
    void testScan_wrapperThrowsIOException_returnsNull() throws Exception {
        Path temp = Files.createTempDirectory("oss-ioe");
        OssScannerService service = spy(new TestableOssScannerService(temp));
        PsiFile psi = mockPsiFile("package.json");
        doReturn(true).when(service).shouldScanFile("package.json",psi);
        try (MockedStatic<DevAssistUtils> utils = mockStatic(DevAssistUtils.class);
             MockedStatic<CxWrapperFactory> factory = mockStatic(CxWrapperFactory.class)) {
            utils.when(() -> DevAssistUtils.getFileContent(psi)).thenReturn("{ }\n");
            factory.when(CxWrapperFactory::build).thenThrow(new IOException("simulated"));
            assertNull(service.scan(psi, temp.resolve("package.json").toString()));
        }
    }

    @Test @DisplayName("createConfig_engineNameIsOSS")
    void testCreateConfig_engineNameIsOSS() {
        assertEquals(ScanEngine.OSS.name(), OssScannerService.createConfig().getEngineName());
    }

    @Test @DisplayName("getIssues_nullResults_returnsEmptyList")
    void testGetIssues_nullResults_returnsEmptyList() {
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(null, "");
        assertTrue(adaptor.getIssues().isEmpty());
    }

    @Test @DisplayName("getIssues_emptyPackages_returnsEmptyList")
    void testGetIssues_emptyPackages_returnsEmptyList() {
        OssRealtimeResults results = mock(OssRealtimeResults.class);
        when(results.getPackages()).thenReturn(List.of());
        OssScanResultAdaptor adaptor = new OssScanResultAdaptor(results,"");
        assertTrue(adaptor.getIssues().isEmpty());
    }
}
