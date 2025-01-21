package com.checkmarx.intellij.unit.settings.global;

import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsSensitiveStateTest {

    @Mock
    private Application mockApplication;

    @Mock
    private PasswordSafe mockPasswordSafe;

    private GlobalSettingsSensitiveState sensitiveState;

    @BeforeEach
    void setUp() {
        sensitiveState = new GlobalSettingsSensitiveState();
    }

    @Test
    void getInstance_ReturnsServiceInstance() {
        // Arrange
        GlobalSettingsSensitiveState expectedInstance = new GlobalSettingsSensitiveState();

        try (MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class)) {
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            when(mockApplication.getService(GlobalSettingsSensitiveState.class)).thenReturn(expectedInstance);

            // Act
            GlobalSettingsSensitiveState result = GlobalSettingsSensitiveState.getInstance();

            // Assert
            assertSame(expectedInstance, result);
            verify(mockApplication).getService(GlobalSettingsSensitiveState.class);
        }
    }

    @Test
    void constructor_ResetsState() {
        // Arrange
        String storedApiKey = "stored-api-key";

        try (MockedStatic<PasswordSafe> passwordSafeMock = mockStatic(PasswordSafe.class)) {
            passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);
            when(mockPasswordSafe.getPassword(any(CredentialAttributes.class))).thenReturn(storedApiKey);

            // Act
            GlobalSettingsSensitiveState newState = new GlobalSettingsSensitiveState();

            // Assert
            assertEquals(storedApiKey, newState.getApiKey());
            verify(mockPasswordSafe).getPassword(any(CredentialAttributes.class));
        }
    }

    @Test
    void apply_WithValidApiKey_StoresCredentials() {
        // Arrange
        String newApiKey = "new-api-key";
        GlobalSettingsSensitiveState newState = new GlobalSettingsSensitiveState();
        newState.setApiKey(newApiKey);

        try (MockedStatic<PasswordSafe> passwordSafeMock = mockStatic(PasswordSafe.class)) {
            passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);

            // Act
            sensitiveState.apply(newState);

            // Assert
            assertEquals(newApiKey, sensitiveState.getApiKey());
            verify(mockPasswordSafe).set(any(CredentialAttributes.class), any(Credentials.class));
        }
    }

    @Test
    void apply_WithBlankApiKey_LogsWarning() {
        // Arrange
        GlobalSettingsSensitiveState newState = new GlobalSettingsSensitiveState();
        newState.setApiKey("");

        try (MockedStatic<PasswordSafe> passwordSafeMock = mockStatic(PasswordSafe.class)) {
            passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);

            // Act
            sensitiveState.apply(newState);

            // Assert
            assertEquals("", sensitiveState.getApiKey());
            verify(mockPasswordSafe).set(any(CredentialAttributes.class), any(Credentials.class));
        }
    }

    @Test
    void reset_LoadsStoredCredentials() {
        // Arrange
        String storedApiKey = "stored-api-key";
        sensitiveState.setApiKey("current-api-key");

        try (MockedStatic<PasswordSafe> passwordSafeMock = mockStatic(PasswordSafe.class)) {
            passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);
            when(mockPasswordSafe.getPassword(any(CredentialAttributes.class))).thenReturn(storedApiKey);

            // Act
            sensitiveState.reset();

            // Assert
            assertEquals(storedApiKey, sensitiveState.getApiKey());
            verify(mockPasswordSafe).getPassword(any(CredentialAttributes.class));
        }
    }

    @Test
    void isValid_WithValidApiKey_ReturnsTrue() {
        // Arrange
        sensitiveState.setApiKey("valid-api-key");

        // Act
        boolean result = sensitiveState.isValid();

        // Assert
        assertTrue(result);
    }

    @Test
    void isValid_WithBlankApiKey_ReturnsFalse() {
        // Arrange
        sensitiveState.setApiKey("");

        // Act
        boolean result = sensitiveState.isValid();

        // Assert
        assertFalse(result);
    }

    @Test
    void isValid_WithNullApiKey_ReturnsFalse() {
        // Arrange
        sensitiveState.setApiKey(null);

        // Act
        boolean result = sensitiveState.isValid();

        // Assert
        assertFalse(result);
    }
} 