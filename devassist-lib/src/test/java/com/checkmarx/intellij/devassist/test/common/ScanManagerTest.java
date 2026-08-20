package com.checkmarx.intellij.devassist.test.common;

import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.common.ScanManager;
import com.checkmarx.intellij.devassist.common.ScannerFactory;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.configuration.ScannerConfig;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
public class ScanManagerTest {

    @Mock private PsiFile mockPsiFile;
    @Mock private ScannerFactory mockScannerFactory;
    @Mock private ScannerService mockScannerService;
    @Mock private ScanResult mockScanResult;
    @Mock private ScannerConfig mockConfig;
    @Mock private ScanIssue mockScanIssue;

    private ScanManager scanManager;

    @BeforeEach
    void setUp() throws Exception {
        scanManager = new ScanManager();
        Field field = ScanManager.class.getDeclaredField("scannerFactory");
        field.setAccessible(true);
        field.set(scanManager, mockScannerFactory);

        lenient().when(mockScannerService.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getEngineName()).thenReturn("ASCA");
        lenient().when(mockScanResult.getIssues()).thenReturn(List.of(mockScanIssue));
    }

    @Test
    @DisplayName("scanFile with null engine routes through all-scanners path")
    void scanFile_withNullEngine_usesAllScanners() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockScannerFactory.getAllSupportedScanners(anyString(), any())).thenReturn(List.of(mockScannerService));
            when(mockScannerService.scan(any(), anyString())).thenReturn(mockScanResult);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, null);

            assertNotNull(result);
            assertEquals(1, result.size());
            verify(mockScannerFactory).getAllSupportedScanners(eq("/path/File.java"), eq(mockPsiFile));
        }
    }

    @Test
    @DisplayName("scanFile with ScanEngine.ALL routes through all-scanners path")
    void scanFile_withAllEngine_usesAllScanners() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockScannerFactory.getAllSupportedScanners(anyString(), any())).thenReturn(List.of(mockScannerService));
            when(mockScannerService.scan(any(), anyString())).thenReturn(mockScanResult);
            when(mockScanResult.getIssues()).thenReturn(Collections.emptyList());

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.ALL);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile with specific engine delegates to that engine's scanner")
    void scanFile_withSpecificEngine_usesSpecificScanner() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockScannerFactory.getSupportedScannerUsingScanEngine(anyString(), any(), eq(ScanEngine.ASCA)))
                    .thenReturn(mockScannerService);
            when(mockScannerService.scan(any(), anyString())).thenReturn(mockScanResult);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.ASCA);

            assertEquals(1, result.size());
            verify(mockScannerFactory).getSupportedScannerUsingScanEngine(anyString(), any(), eq(ScanEngine.ASCA));
        }
    }

    @Test
    @DisplayName("scanFile returns empty list when factory finds no scanners")
    void scanFile_whenNoSupportedScanners_returnsEmpty() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            when(mockScannerFactory.getAllSupportedScanners(anyString(), any())).thenReturn(Collections.emptyList());

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, null);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile returns empty list when specific scanner is not active")
    void scanFile_specificEngine_whenScannerNotActive_returnsEmpty() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(false);
            when(mockScannerFactory.getSupportedScannerUsingScanEngine(anyString(), any(), any()))
                    .thenReturn(mockScannerService);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.ASCA);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile swallows scanner exception and returns empty list")
    void scanFile_whenScannerThrowsException_returnsEmpty() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockScannerFactory.getAllSupportedScanners(anyString(), any())).thenReturn(List.of(mockScannerService));
            when(mockScannerService.scan(any(), anyString())).thenThrow(new RuntimeException("scan engine failure"));

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile returns empty list when no scanner registered for engine")
    void scanFile_specificEngine_whenNoScannerForEngine_returnsEmpty() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            when(mockScannerFactory.getSupportedScannerUsingScanEngine(anyString(), any(), any()))
                    .thenReturn(null);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.IAC);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile returns empty list when scan returns null result")
    void scanFile_specificEngine_whenScanReturnsNull_returnsEmpty() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockScannerFactory.getSupportedScannerUsingScanEngine(anyString(), any(), any()))
                    .thenReturn(mockScannerService);
            when(mockScannerService.scan(any(), anyString())).thenReturn(null);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.ASCA);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("getSupportedEnabledScanner filters out inactive scanners")
    void getSupportedEnabledScanner_filtersInactiveScanners() {
        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive("ASCA")).thenReturn(false);
            when(mockScannerFactory.getAllSupportedScanners(anyString(), any())).thenReturn(List.of(mockScannerService));

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, ScanEngine.ALL);

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("scanFile with ALL engine aggregates issues from multiple scanners")
    void scanFile_withAllEngine_aggregatesIssuesFromMultipleScanners() {
        ScannerService mockService2 = mock(ScannerService.class);
        ScannerConfig mockConfig2 = mock(ScannerConfig.class);
        ScanResult mockResult2 = mock(ScanResult.class);
        ScanIssue mockIssue2 = mock(ScanIssue.class);

        try (MockedStatic<DevAssistUtils> devAssistMock = mockStatic(DevAssistUtils.class)) {
            devAssistMock.when(() -> DevAssistUtils.isScannerActive(anyString())).thenReturn(true);
            when(mockService2.getConfig()).thenReturn(mockConfig2);
            when(mockConfig2.getEngineName()).thenReturn("CONTAINERS");
            when(mockResult2.getIssues()).thenReturn(List.of(mockIssue2));

            when(mockScannerFactory.getAllSupportedScanners(anyString(), any()))
                    .thenReturn(List.of(mockScannerService, mockService2));
            when(mockScannerService.scan(any(), anyString())).thenReturn(mockScanResult);
            when(mockService2.scan(any(), anyString())).thenReturn(mockResult2);

            List<ScanIssue> result = scanManager.scanFile("/path/File.java", mockPsiFile, null);

            assertEquals(2, result.size());
        }
    }
}
