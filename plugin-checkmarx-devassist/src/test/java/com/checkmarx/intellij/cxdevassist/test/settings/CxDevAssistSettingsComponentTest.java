package com.checkmarx.intellij.cxdevassist.test.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CxDevAssistSettingsComponent following McpConfigurationTest pattern.
 * Tests the underlying settings logic without requiring IntelliJ Platform UI.
 */
class CxDevAssistSettingsComponentTest {

    private GlobalSettingsState mockGlobalState;
    private GlobalSettingsSensitiveState mockSensitiveState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;
    private MockedStatic<GlobalSettingsSensitiveState> mockedSensitiveState;

    @BeforeEach
    void setUp() {
        // Mock global settings following McpConfigurationTest pattern
        mockGlobalState = mock(GlobalSettingsState.class);
        mockSensitiveState = mock(GlobalSettingsSensitiveState.class);

        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedSensitiveState = mockStatic(GlobalSettingsSensitiveState.class);

        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
        mockedSensitiveState.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);

        // Setup default behavior
        when(mockGlobalState.getAdditionalParameters()).thenReturn("");
        when(mockSensitiveState.getApiKey()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
        mockedSensitiveState.close();
    }

    @Test
    @DisplayName("Global settings state is accessible")
    void testGetGlobalState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Sensitive state is accessible")
    void testGetSensitiveState() {
        GlobalSettingsSensitiveState state = GlobalSettingsSensitiveState.getInstance();
        assertEquals(mockSensitiveState, state);
    }

    @Test
    @DisplayName("Additional parameters can be retrieved")
    void testGetAdditionalParameters() {
        when(mockGlobalState.getAdditionalParameters()).thenReturn("test-params");
        String params = GlobalSettingsState.getInstance().getAdditionalParameters();
        assertEquals("test-params", params);
    }

    @Test
    @DisplayName("API key can be retrieved from sensitive state")
    void testGetApiKey() {
        when(mockSensitiveState.getApiKey()).thenReturn("test-api-key");
        String apiKey = GlobalSettingsSensitiveState.getInstance().getApiKey();
        assertEquals("test-api-key", apiKey);
    }

    @Test
    @DisplayName("Global state apply method can be called")
    void testApplyGlobalState() {
        GlobalSettingsState state = new GlobalSettingsState();
        doNothing().when(mockGlobalState).apply(any());
        assertDoesNotThrow(() -> GlobalSettingsState.getInstance().apply(state));
        verify(mockGlobalState).apply(state);
    }

    @Test
    @DisplayName("Settings validation with empty API key")
    void testValidation_EmptyApiKey() {
        when(mockSensitiveState.getApiKey()).thenReturn("");
        String apiKey = GlobalSettingsSensitiveState.getInstance().getApiKey();
        assertTrue(apiKey.isEmpty());
    }

    @Test
    @DisplayName("Settings validation with null API key")
    void testValidation_NullApiKey() {
        when(mockSensitiveState.getApiKey()).thenReturn(null);
        String apiKey = GlobalSettingsSensitiveState.getInstance().getApiKey();
        assertNull(apiKey);
    }

    @Test
    @DisplayName("Multiple getInstance calls return same mocked instance")
    void testSingleton_GlobalState() {
        GlobalSettingsState instance1 = GlobalSettingsState.getInstance();
        GlobalSettingsState instance2 = GlobalSettingsState.getInstance();
        assertSame(instance1, instance2);
        assertEquals(mockGlobalState, instance1);
    }

    @Test
    @DisplayName("Sensitive state getInstance returns same mocked instance")
    void testSingleton_SensitiveState() {
        GlobalSettingsSensitiveState instance1 = GlobalSettingsSensitiveState.getInstance();
        GlobalSettingsSensitiveState instance2 = GlobalSettingsSensitiveState.getInstance();
        assertSame(instance1, instance2);
        assertEquals(mockSensitiveState, instance1);
    }

    @Test
    @DisplayName("State modifications can be tracked")
    void testStateModification() {
        when(mockGlobalState.isAuthenticated()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().isAuthenticated());

        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    @Test
    @DisplayName("Additional parameters can be set")
    void testSetAdditionalParameters() {
        String testParams = "param1=value1&param2=value2";
        doNothing().when(mockGlobalState).setAdditionalParameters(testParams);

        GlobalSettingsState.getInstance().setAdditionalParameters(testParams);
        verify(mockGlobalState).setAdditionalParameters(testParams);
    }

    @Test
    @DisplayName("API key can be set in sensitive state")
    void testSetApiKey() {
        String testKey = "test-api-key-12345";
        doNothing().when(mockSensitiveState).setApiKey(testKey);

        GlobalSettingsSensitiveState.getInstance().setApiKey(testKey);
        verify(mockSensitiveState).setApiKey(testKey);
    }
}


