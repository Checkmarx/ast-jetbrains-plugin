package com.checkmarx.intellij.common.settings;

import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GlobalSettingsSensitiveStateTest {

    private MockedStatic<PasswordSafe> psMock;
    private MockedStatic<ApplicationManager> appMock;
    private PasswordSafe mockPasswordSafe;
    private GlobalSettingsSensitiveState instance;

    @BeforeEach
    void setUp() {
        mockPasswordSafe = mock(PasswordSafe.class);
        psMock = mockStatic(PasswordSafe.class);
        appMock = mockStatic(ApplicationManager.class, RETURNS_DEEP_STUBS);

        psMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);
        lenient().when(mockPasswordSafe.get(any())).thenReturn(null);

        instance = new GlobalSettingsSensitiveState();
    }

    @AfterEach
    void tearDown() {
        psMock.close();
        appMock.close();
    }

    @Test
    @DisplayName("constructor initializes apiKey and refreshToken to null when secure storage is empty")
    void constructor_withEmptyStorage_setsFieldsToNull() {
        assertNull(instance.getApiKey());
        assertNull(instance.getRefreshToken());
    }

    @Test
    @DisplayName("setApiKey and getApiKey round-trip")
    void setApiKey_thenGetApiKey_returnsSetValue() {
        instance.setApiKey("my-api-key-123");
        assertEquals("my-api-key-123", instance.getApiKey());
    }

    @Test
    @DisplayName("setRefreshToken and getRefreshToken round-trip")
    void setRefreshToken_thenGetRefreshToken_returnsSetValue() {
        instance.setRefreshToken("refresh-token-abc");
        assertEquals("refresh-token-abc", instance.getRefreshToken());
    }

    @Test
    @DisplayName("setApiKey with null clears the field")
    void setApiKey_withNull_clearsField() {
        instance.setApiKey("existing");
        instance.setApiKey(null);
        assertNull(instance.getApiKey());
    }

    @Test
    @DisplayName("isTokenExpired returns false for blank expiry string")
    void isTokenExpired_blankString_returnsFalse() {
        assertFalse(instance.isTokenExpired(""));
        assertFalse(instance.isTokenExpired(null));
        assertFalse(instance.isTokenExpired("   "));
    }

    @Test
    @DisplayName("isTokenExpired returns true for a date in the past")
    void isTokenExpired_pastDate_returnsTrue() {
        String pastDate = LocalDateTime.now().minusDays(1).toString();
        assertTrue(instance.isTokenExpired(pastDate));
    }

    @Test
    @DisplayName("isTokenExpired returns false for a date in the future")
    void isTokenExpired_futureDate_returnsFalse() {
        String futureDate = LocalDateTime.now().plusDays(1).toString();
        assertFalse(instance.isTokenExpired(futureDate));
    }

    @Test
    @DisplayName("saveRefreshToken calls PasswordSafe.set")
    void saveRefreshToken_callsPasswordSafeSet() {
        instance.saveRefreshToken("new-refresh-token");
        verify(mockPasswordSafe).set(any(), any(Credentials.class));
    }

    @Test
    @DisplayName("deleteRefreshToken sets refreshToken field to null")
    void deleteRefreshToken_setsFieldToNull() {
        instance.setRefreshToken("token-to-delete");
        instance.deleteRefreshToken();
        assertNull(instance.getRefreshToken());
    }

    @Test
    @DisplayName("deleteRefreshToken calls PasswordSafe.set with null credentials")
    void deleteRefreshToken_callsPasswordSafeWithNull() {
        instance.deleteRefreshToken();
        verify(mockPasswordSafe).set(any(), isNull());
    }

    @Test
    @DisplayName("equals returns true for two instances with same field values")
    void equals_sameFieldValues_returnsTrue() {
        GlobalSettingsSensitiveState other = new GlobalSettingsSensitiveState();
        instance.setApiKey("key");
        instance.setRefreshToken("token");
        other.setApiKey("key");
        other.setRefreshToken("token");
        assertEquals(instance, other);
    }

    @Test
    @DisplayName("equals returns false for two instances with different field values")
    void equals_differentFieldValues_returnsFalse() {
        GlobalSettingsSensitiveState other = new GlobalSettingsSensitiveState();
        instance.setApiKey("key1");
        other.setApiKey("key2");
        assertNotEquals(instance, other);
    }

    @Test
    @DisplayName("hashCode is consistent for same data")
    void hashCode_sameData_isConsistent() {
        instance.setApiKey("key");
        instance.setRefreshToken("token");
        int h1 = instance.hashCode();
        int h2 = instance.hashCode();
        assertEquals(h1, h2);
    }

    @Test
    @DisplayName("constructor loads credentials from PasswordSafe when present")
    void constructor_withStoredCredentials_loadsValues() {
        Credentials mockCredentials = mock(Credentials.class);
        when(mockCredentials.getPasswordAsString()).thenReturn("stored-token");
        when(mockPasswordSafe.get(any())).thenReturn(mockCredentials);

        GlobalSettingsSensitiveState loaded = new GlobalSettingsSensitiveState();

        assertEquals("stored-token", loaded.getApiKey());
        assertEquals("stored-token", loaded.getRefreshToken());
    }

    // ===== validate / isValid =====

    @Test
    @DisplayName("isValid returns false when API key mode enabled but apiKey is blank")
    void isValid_apiKeyEnabled_apiKeyBlank_returnsFalse() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(true);
        instance.setApiKey(null);

        assertFalse(instance.isValid(settings));
    }

    @Test
    @DisplayName("isValid returns true when API key mode enabled and apiKey is present")
    void isValid_apiKeyEnabled_apiKeyPresent_returnsTrue() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(true);
        instance.setApiKey("my-api-key");

        assertTrue(instance.isValid(settings));
    }

    @Test
    @DisplayName("isValid returns false when OAuth mode and refreshToken is blank")
    void isValid_oauthMode_emptyRefreshToken_returnsFalse() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(false);
        instance.setRefreshToken(null);

        assertFalse(instance.isValid(settings));
    }

    @Test
    @DisplayName("isValid returns true when OAuth mode and valid non-expired refreshToken")
    void isValid_oauthMode_validToken_validExpiry_returnsTrue() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(false);
        String futureExpiry = LocalDateTime.now().plusDays(1).toString();
        settings.setRefreshTokenExpiry(futureExpiry);
        instance.setRefreshToken("valid-refresh-token");

        assertTrue(instance.isValid(settings));
    }

    @Test
    @DisplayName("isValid returns false when OAuth mode and refreshToken is expired")
    void isValid_oauthMode_expiredToken_returnsFalse() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(false);
        String pastExpiry = LocalDateTime.now().minusDays(1).toString();
        settings.setRefreshTokenExpiry(pastExpiry);
        instance.setRefreshToken("expired-refresh-token");

        assertFalse(instance.isValid(settings));
    }

    // ===== apply =====

    @Test
    @DisplayName("apply copies state and calls store (PasswordSafe.set is invoked)")
    void apply_validState_storesCredentials() {
        GlobalSettingsState settings = new GlobalSettingsState();
        settings.setApiKeyEnabled(true);

        GlobalSettingsSensitiveState newState = new GlobalSettingsSensitiveState();
        newState.setApiKey("new-api-key");
        newState.setRefreshToken("new-refresh");

        instance.apply(settings, newState);

        assertEquals("new-api-key", instance.getApiKey());
        assertEquals("new-refresh", instance.getRefreshToken());
        // store() calls saveSecret which calls PasswordSafe.set at least twice (apiKey + refreshToken)
        verify(mockPasswordSafe, atLeastOnce()).set(any(), any());
    }

    // ===== getInstance =====

    @Test
    @DisplayName("getInstance delegates to ApplicationManager.getApplication().getService()")
    void getInstance_mockedApplicationManager_returnsInstance() {
        Application mockApplication = mock(Application.class);
        GlobalSettingsSensitiveState expectedInstance = new GlobalSettingsSensitiveState();
        when(mockApplication.getService(GlobalSettingsSensitiveState.class)).thenReturn(expectedInstance);
        appMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);

        GlobalSettingsSensitiveState result = GlobalSettingsSensitiveState.getInstance();

        assertSame(expectedInstance, result);
    }
}
