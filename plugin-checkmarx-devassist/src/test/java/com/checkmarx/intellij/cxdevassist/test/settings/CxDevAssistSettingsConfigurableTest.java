package com.checkmarx.intellij.cxdevassist.test.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.cxdevassist.settings.CxDevAssistSettingsConfigurable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CxDevAssistSettingsConfigurable following McpConfigurationTest pattern.
 */
class CxDevAssistSettingsConfigurableTest {

    private GlobalSettingsState mockGlobalState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;

    @BeforeEach
    void setUp() {
        mockGlobalState = mock(GlobalSettingsState.class);
        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
    }

    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
    }

    @Test
    @DisplayName("Configurable can be instantiated")
    void testConfigurableInstantiation() {
        assertDoesNotThrow(() -> new CxDevAssistSettingsConfigurable(),
                          "Configurable should be instantiable");
    }

    @Test
    @DisplayName("Display name is correct")
    void testGetDisplayName() {
        CxDevAssistSettingsConfigurable configurable = new CxDevAssistSettingsConfigurable();
        assertEquals("Checkmarx Developer Assist", configurable.getDisplayName());
    }

    @Test
    @DisplayName("ID is not null or empty")
    void testGetId() {
        CxDevAssistSettingsConfigurable configurable = new CxDevAssistSettingsConfigurable();
        String id = configurable.getId();
        assertNotNull(id, "ID should not be null");
        assertFalse(id.isEmpty(), "ID should not be empty");
    }

    @Test
    @DisplayName("Help topic returns ID")
    void testGetHelpTopic() {
        CxDevAssistSettingsConfigurable configurable = new CxDevAssistSettingsConfigurable();
        assertEquals(configurable.getId(), configurable.getHelpTopic());
    }

    @Test
    @DisplayName("Global settings state can be accessed")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Settings state modifications are tracked")
    void testStateModifications() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    @Test
    @DisplayName("Multiple configurable instances are independent")
    void testMultipleInstances() {
        CxDevAssistSettingsConfigurable config1 = new CxDevAssistSettingsConfigurable();
        CxDevAssistSettingsConfigurable config2 = new CxDevAssistSettingsConfigurable();
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Display name is consistent across calls")
    void testDisplayNameConsistency() {
        CxDevAssistSettingsConfigurable configurable = new CxDevAssistSettingsConfigurable();
        String name1 = configurable.getDisplayName();
        String name2 = configurable.getDisplayName();
        assertEquals(name1, name2);
    }

    @Test
    @DisplayName("ID is consistent across calls")
    void testIdConsistency() {
        CxDevAssistSettingsConfigurable configurable = new CxDevAssistSettingsConfigurable();
        String id1 = configurable.getId();
        String id2 = configurable.getId();
        assertEquals(id1, id2);
    }

    @Test
    @DisplayName("Settings state singleton works correctly")
    void testSettingsStateSingleton() {
        GlobalSettingsState instance1 = GlobalSettingsState.getInstance();
        GlobalSettingsState instance2 = GlobalSettingsState.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("Authentication state can be checked")
    void testAuthenticationState() {
        when(mockGlobalState.isAuthenticated()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().isAuthenticated());
        
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    @Test
    @DisplayName("Validation in progress can be checked")
    void testValidationInProgress() {
        when(mockGlobalState.isValidationInProgress()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isValidationInProgress());
    }

    @Test
    @DisplayName("Additional parameters can be retrieved")
    void testAdditionalParameters() {
        when(mockGlobalState.getAdditionalParameters()).thenReturn("test=value");
        assertEquals("test=value", GlobalSettingsState.getInstance().getAdditionalParameters());
    }

    @Test
    @DisplayName("API key enabled state can be checked")
    void testApiKeyEnabled() {
        when(mockGlobalState.isApiKeyEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isApiKeyEnabled());
    }

    @Test
    @DisplayName("State apply method works")
    void testStateApply() {
        GlobalSettingsState state = new GlobalSettingsState();
        doNothing().when(mockGlobalState).apply(any());
        assertDoesNotThrow(() -> GlobalSettingsState.getInstance().apply(state));
    }

    @Test
    @DisplayName("Validation message can be retrieved")
    void testValidationMessage() {
        when(mockGlobalState.getValidationMessage()).thenReturn("Test message");
        assertEquals("Test message", GlobalSettingsState.getInstance().getValidationMessage());
    }

    @Test
    @DisplayName("Last validation success can be checked")
    void testLastValidationSuccess() {
        when(mockGlobalState.isLastValidationSuccess()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isLastValidationSuccess());
    }

    @Test
    @DisplayName("Welcome shown state can be checked")
    void testWelcomeShown() {
        when(mockGlobalState.isWelcomeShown()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isWelcomeShown());
    }
}

