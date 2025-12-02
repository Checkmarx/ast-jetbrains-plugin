package com.checkmarx.intellij.unit.devassist.scanners.secrets;

import com.checkmarx.ast.secretsrealtime.SecretsRealtimeResults;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.devassist.scanners.secrets.SecretsScannerService;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SecretsScannerServiceTest {

    private SecretsScannerService secretsScannerService;
    private PsiFile mockPsiFile;
    private CxWrapper mockWrapper;

    @BeforeEach
    void setUp() {
        secretsScannerService = new SecretsScannerService();
        mockPsiFile = mock(PsiFile.class);
        mockWrapper = mock(CxWrapper.class);
    }

    @Test
    @DisplayName("createConfig: returns non-null config with expected engine name")
    void testCreateConfig() {
        var config = SecretsScannerService.createConfig();
        assertNotNull(config, "ScannerConfig should not be null");
        assertEquals("SECRETS", config.getEngineName(), "Engine name must be SECRETS");
        assertNotNull(config.getConfigSection());
        assertNotNull(config.getActivateKey());
    }

    @Test
    @DisplayName("shouldScanFile: basic inclusion/exclusion behavior")
    void testShouldScanFile() {
        // positive cases
        assertTrue(secretsScannerService.shouldScanFile("src/app.js"));
        assertTrue(secretsScannerService.shouldScanFile(".env"));
        assertTrue(secretsScannerService.shouldScanFile("Dockerfile"));

        // known exclusions
        assertFalse(secretsScannerService.shouldScanFile("project/node_modules/package.json"));
        assertFalse(secretsScannerService.shouldScanFile("project/.vscode/.checkmarxIgnored"));
    }

    @Test
    @DisplayName("scan: returns null for files that are not eligible (node_modules)")
    void testScan_FileNotEligible() {
        String filePath = "project/node_modules/test.js";
        when(mockPsiFile.getName()).thenReturn("test.js");

        var result = secretsScannerService.scan(mockPsiFile, filePath);
        assertNull(result, "scan should return null for paths excluded from scanning");
    }

    @Test
    @DisplayName("scan: integrates with wrapper and returns adaptor when wrapper returns results")
    void testScan_WithMockedWrapper() throws Exception {
        // Arrange - stable content and a mocked wrapper + results
        when(mockPsiFile.getName()).thenReturn("test.js");

        try (MockedStatic<DevAssistUtils> devAssistUtilsStatic = mockStatic(DevAssistUtils.class)) {
            devAssistUtilsStatic.when(() -> DevAssistUtils.getFileContent(mockPsiFile)).thenReturn("file contents");

            SecretsRealtimeResults mockResults = mock(SecretsRealtimeResults.class);
            when(mockResults.getSecrets()).thenReturn(List.of());

            try (MockedStatic<CxWrapperFactory> wrapperFactoryStatic = mockStatic(CxWrapperFactory.class)) {
                wrapperFactoryStatic.when(CxWrapperFactory::build).thenReturn(mockWrapper);
                when(mockWrapper.secretsRealtimeScan(anyString(), anyString())).thenReturn(mockResults);

                // Act
                ScanResult<SecretsRealtimeResults> result = secretsScannerService.scan(mockPsiFile, "test.js");

                // Assert
                assertNotNull(result, "Scan should return an adaptor/wrapper result when underlying wrapper returns a non-null object");
            }
        }
    }

    @Test
    @DisplayName("getTempSubFolderPath: builds a path containing the file base name and 'secrets' dir")
    void testGetTempSubFolderPath() throws Exception {
        when(mockPsiFile.getName()).thenReturn("test.js");

        var method = SecretsScannerService.class.getDeclaredMethod("getTempSubFolderPath", PsiFile.class);
        method.setAccessible(true);

        Path result = (Path) method.invoke(secretsScannerService, mockPsiFile);
        assertNotNull(result);
        String asString = result.toString();
        assertTrue(asString.contains("test.js"), "temp path should contain original file name");
        assertTrue(asString.toLowerCase().contains("secrets") || asString.contains("secrets"), "temp path should be under secrets directory name");
    }

    @Test
    @DisplayName("scan: handles empty content gracefully (does not throw)")
    void testScan_EmptyContentHandled() {
        when(mockPsiFile.getName()).thenReturn("test.js");

        try (MockedStatic<DevAssistUtils> devAssistUtilsStatic = mockStatic(DevAssistUtils.class)) {

            devAssistUtilsStatic.when(() -> DevAssistUtils.getFileContent(mockPsiFile)).thenReturn("");

            assertDoesNotThrow(() -> {
                var result = secretsScannerService.scan(mockPsiFile, "test.js");
                assertNull(result, "scan should return null when file content is empty");
            });
        }
    }
}
