package com.checkmarx.intellij.unit.commands;

import com.checkmarx.ast.scan.Scan;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanTest {

    @Mock
    private CxWrapper mockWrapper;

    @Mock
    private Scan mockScan;

    @BeforeEach
    void setUp() {
        mockScan = mock(Scan.class);
    }

    @Test
    void getLatestScanId_Success() throws IOException, CxException, InterruptedException {
        // Arrange
        List<Scan> scans = Arrays.asList(mockScan);
        String expectedScanId = "test-scan-id";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.scanList()).thenReturn(scans);
            when(mockScan.getId()).thenReturn(expectedScanId);

            // Act
            String result = com.checkmarx.intellij.commands.Scan.getLatestScanId();

            // Assert
            assertNotNull(result);
            assertEquals(expectedScanId, result);
            verify(mockWrapper).scanList();
            verify(mockScan).getId();
        }
    }

    @Test
    void getList_Success() throws IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        String projectId = "test-project";
        String branch = "main";
        List<Scan> expectedScans = Arrays.asList(mockScan);
        String expectedFilter = "project-id=test-project,branch=main,limit=10000,statuses=Completed";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.scanList(expectedFilter)).thenReturn(expectedScans);

            // Act
            List<Scan> result = com.checkmarx.intellij.commands.Scan.getList(projectId, branch);

            // Assert
            assertNotNull(result);
            assertEquals(expectedScans, result);
            verify(mockWrapper).scanList(expectedFilter);
        }
    }

    @Test
    void scanShow_Success() throws IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        String scanId = UUID.randomUUID().toString();
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.scanShow(any(UUID.class))).thenReturn(mockScan);

            // Act
            Scan result = com.checkmarx.intellij.commands.Scan.scanShow(scanId);

            // Assert
            assertNotNull(result);
            assertEquals(mockScan, result);
            verify(mockWrapper).scanShow(UUID.fromString(scanId));
        }
    }

    @Test
    void scanCreate_Success() throws IOException, CxException, InterruptedException {
        // Arrange
        String sourcePath = "/test/path";
        String projectName = "test-project";
        String branchName = "main";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.scanCreate(any(Map.class), anyString())).thenReturn(mockScan);

            // Act
            Scan result = com.checkmarx.intellij.commands.Scan.scanCreate(sourcePath, projectName, branchName);

            // Assert
            assertNotNull(result);
            assertEquals(mockScan, result);
            verify(mockWrapper).scanCreate(argThat(map -> 
                map.get("-s").equals(sourcePath) &&
                map.get("--project-name").equals(projectName) &&
                map.get("--branch").equals(branchName) &&
                map.get("--agent").equals(Constants.JET_BRAINS_AGENT_NAME)
            ), eq("--async --sast-incremental --resubmit"));
        }
    }

    @Test
    void scanCancel_Success() throws IOException, CxException, InterruptedException {
        // Arrange
        String scanId = "test-scan-id";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            doNothing().when(mockWrapper).scanCancel(anyString());

            // Act & Assert
            assertDoesNotThrow(() -> {
                com.checkmarx.intellij.commands.Scan.scanCancel(scanId);
                verify(mockWrapper).scanCancel(scanId);
            });
        }
    }

    @Test
    void scanCancel_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        String scanId = "test-scan-id";
        
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            doThrow(mock(CxException.class)).when(mockWrapper).scanCancel(anyString());

            // Act & Assert
            assertThrows(CxException.class, () ->
                com.checkmarx.intellij.commands.Scan.scanCancel(scanId)
            );
        }
    }
} 