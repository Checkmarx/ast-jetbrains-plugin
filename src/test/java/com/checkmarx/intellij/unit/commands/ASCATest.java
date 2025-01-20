package com.checkmarx.intellij.unit.commands;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.commands.ASCA;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ASCATest {

    @Mock
    private CxWrapper mockWrapper;

    @Mock
    private ScanResult mockScanResult;

    @BeforeEach
    void setUp() {
        mockScanResult = mock(ScanResult.class);
    }

    @Test
    void scanAsca_Success() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        String testPath = "/test/path";
        boolean ascaLatestVersion = true;
        String agent = "test-agent";

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ScanAsca(eq(testPath), eq(ascaLatestVersion), eq(agent))).thenReturn(mockScanResult);

            // Act
            ScanResult result = ASCA.scanAsca(testPath, ascaLatestVersion, agent);

            // Assert
            assertNotNull(result);
            assertEquals(mockScanResult, result);
            verify(mockWrapper).ScanAsca(testPath, ascaLatestVersion, agent);
        }
    }

    @Test
    void scanAsca_ThrowsException() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        String testPath = "/test/path";
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ScanAsca(anyString(), anyBoolean(), anyString()))
                .thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                ASCA.scanAsca(testPath, true, "test-agent")
            );
        }
    }

    @Test
    void installAsca_Success() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ScanAsca(eq(""), eq(true), eq(Constants.JET_BRAINS_AGENT_NAME))).thenReturn(mockScanResult);

            // Act
            ScanResult result = ASCA.installAsca();

            // Assert
            assertNotNull(result);
            assertEquals(mockScanResult, result);
            verify(mockWrapper).ScanAsca("", true, Constants.JET_BRAINS_AGENT_NAME);
        }
    }

    @Test
    void installAsca_ThrowsException() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ScanAsca(anyString(), anyBoolean(), anyString()))
                .thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                ASCA.installAsca()
            );
        }
    }
} 