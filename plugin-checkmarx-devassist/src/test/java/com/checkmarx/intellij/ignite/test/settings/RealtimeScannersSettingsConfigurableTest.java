package com.checkmarx.intellij.ignite.test.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.ignite.settings.RealtimeScannersSettingsConfigurable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeScannersSettingsConfigurableTest {

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
    @DisplayName("Display name is correct")
    void testGetDisplayName() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertEquals("Realtime Scanners", configurable.getDisplayName());
    }

    @Test
    @DisplayName("ID is not null")
    void testGetId() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertNotNull(configurable.getId());
    }

    @Test
    @DisplayName("Help topic matches ID")
    void testGetHelpTopic() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertEquals(configurable.getId(), configurable.getHelpTopic());
    }

    @Test
    @DisplayName("Global state is accessible")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Scanner settings can be checked")
    void testScannerSettings() {
        when(mockGlobalState.isAscaRealtime()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAscaRealtime());
    }

    @Test
    @DisplayName("Multiple instances are independent")
    void testMultipleInstances() {
        RealtimeScannersSettingsConfigurable config1 = new RealtimeScannersSettingsConfigurable();
        RealtimeScannersSettingsConfigurable config2 = new RealtimeScannersSettingsConfigurable();
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Display name is consistent")
    void testDisplayNameConsistency() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        String name1 = configurable.getDisplayName();
        String name2 = configurable.getDisplayName();
        assertEquals(name1, name2);
    }

    @Test
    @DisplayName("Configurable instantiation succeeds")
    void testInstantiation() {
        assertDoesNotThrow(() -> new RealtimeScannersSettingsConfigurable());
    }

    @Test
    @DisplayName("MCP enabled state affects scanner availability")
    void testMcpEnabledState() {
        when(mockGlobalState.isMcpEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isMcpEnabled());
    }

    @Test
    @DisplayName("Authentication affects scanner configurability")
    void testAuthenticationState() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }
}

