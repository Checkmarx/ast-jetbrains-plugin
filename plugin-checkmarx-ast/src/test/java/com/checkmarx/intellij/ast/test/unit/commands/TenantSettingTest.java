package com.checkmarx.intellij.ast.test.unit.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

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
    void isScanAllowed_ReturnsTrue() throws IOException, CxException, InterruptedException {
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
    void isScanAllowed_ReturnsFalse() throws IOException, CxException, InterruptedException {
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
    void isScanAllowed_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, TenantSetting::isScanAllowed);
        }
    }


    @Test
    void isAiMcpServerEnabled_WithExplicitState_ReturnsTrue() throws IOException, CxException, InterruptedException {
        // Arrange
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitiveState = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(() -> CxWrapperFactory.build(mockState, mockSensitiveState)).thenReturn(mockWrapper);
            when(mockWrapper.aiMcpServerEnabled()).thenReturn(true);

            // Act
            boolean result = TenantSetting.isAiMcpServerEnabled(mockState, mockSensitiveState);

            // Assert
            assertTrue(result);
            verify(mockWrapper).aiMcpServerEnabled();
            mockedFactory.verify(() -> CxWrapperFactory.build(mockState, mockSensitiveState));
        }
    }

    @Test
    void isAiMcpServerEnabled_WithExplicitState_ThrowsException() throws IOException, CxException, InterruptedException {
        // Arrange
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState mockSensitiveState = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<CxWrapperFactory> mockedFactory = mockStatic(CxWrapperFactory.class)) {
            mockedFactory.when(() -> CxWrapperFactory.build(mockState, mockSensitiveState)).thenReturn(mockWrapper);
            when(mockWrapper.aiMcpServerEnabled()).thenThrow(mock(CxException.class));

            // Act & Assert
            assertThrows(CxException.class, () -> TenantSetting.isAiMcpServerEnabled(mockState, mockSensitiveState));
        }
    }
}