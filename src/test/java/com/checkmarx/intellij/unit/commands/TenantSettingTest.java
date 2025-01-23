package com.checkmarx.intellij.unit.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.commands.TenantSetting;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantSettingTest {

    @Mock
    private CxWrapper mockWrapper;

    @BeforeEach
    void setUp() {
    }

    @Test
    void isScanAllowed_ReturnsTrue() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenReturn(true);

            // Act
            boolean result = TenantSetting.isScanAllowed();

            // Assert
            assertTrue(result);
            verify(mockWrapper).ideScansEnabled();
        }
    }

    @Test
    void isScanAllowed_ReturnsFalse() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenReturn(false);

            // Act
            boolean result = TenantSetting.isScanAllowed();

            // Assert
            assertFalse(result);
            verify(mockWrapper).ideScansEnabled();
        }
    }

    @Test
    void isScanAllowed_ThrowsException() throws CxConfig.InvalidCLIConfigException, IOException, URISyntaxException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () ->
                TenantSetting.isScanAllowed()
            );
        }
    }
} 