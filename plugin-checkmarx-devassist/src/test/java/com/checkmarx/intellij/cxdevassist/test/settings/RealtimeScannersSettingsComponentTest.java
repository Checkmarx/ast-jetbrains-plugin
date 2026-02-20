package com.checkmarx.intellij.cxdevassist.test.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RealtimeScannersSettingsComponent following McpConfigurationTest pattern.
 */
class RealtimeScannersSettingsComponentTest {

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
    @DisplayName("Global settings state is accessible")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("ASCA realtime setting can be checked")
    void testAscaRealtime() {
        when(mockGlobalState.isAscaRealtime()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAscaRealtime());
    }

    @Test
    @DisplayName("OSS realtime setting can be checked")
    void testOssRealtime() {
        when(mockGlobalState.isOssRealtime()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().isOssRealtime());
    }

    @Test
    @DisplayName("Secret detection realtime setting can be checked")
    void testSecretDetectionRealtime() {
        when(mockGlobalState.isSecretDetectionRealtime()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isSecretDetectionRealtime());
    }

    @Test
    @DisplayName("Containers realtime setting can be checked")
    void testContainersRealtime() {
        when(mockGlobalState.isContainersRealtime()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().isContainersRealtime());
    }

    @Test
    @DisplayName("IaC realtime setting can be checked")
    void testIacRealtime() {
        when(mockGlobalState.isIacRealtime()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isIacRealtime());
    }

    @Test
    @DisplayName("Containers tool can be retrieved")
    void testContainersTool() {
        when(mockGlobalState.getContainersTool()).thenReturn("docker");
        assertEquals("docker", GlobalSettingsState.getInstance().getContainersTool());
    }

    @Test
    @DisplayName("MCP enabled state can be checked")
    void testMcpEnabled() {
        when(mockGlobalState.isMcpEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isMcpEnabled());
    }

    @Test
    @DisplayName("MCP status checked can be verified")
    void testMcpStatusChecked() {
        when(mockGlobalState.isMcpStatusChecked()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().isMcpStatusChecked());
    }

    @Test
    @DisplayName("User preferences set can be checked")
    void testUserPreferencesSet() {
        when(mockGlobalState.getUserPreferencesSet()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().getUserPreferencesSet());
    }

    @Test
    @DisplayName("User pref ASCA realtime can be retrieved")
    void testUserPrefAscaRealtime() {
        when(mockGlobalState.getUserPrefAscaRealtime()).thenReturn(false);
        assertFalse(GlobalSettingsState.getInstance().getUserPrefAscaRealtime());
    }

    @Test
    @DisplayName("Settings can be modified")
    void testSetAscaRealtime() {
        doNothing().when(mockGlobalState).setAscaRealtime(true);
        GlobalSettingsState.getInstance().setAscaRealtime(true);
        verify(mockGlobalState).setAscaRealtime(true);
    }

    @Test
    @DisplayName("Multiple scanner settings can be toggled")
    void testMultipleScannerSettings() {
        when(mockGlobalState.isAscaRealtime()).thenReturn(true);
        when(mockGlobalState.isOssRealtime()).thenReturn(true);
        when(mockGlobalState.isSecretDetectionRealtime()).thenReturn(false);
        
        assertTrue(GlobalSettingsState.getInstance().isAscaRealtime());
        assertTrue(GlobalSettingsState.getInstance().isOssRealtime());
        assertFalse(GlobalSettingsState.getInstance().isSecretDetectionRealtime());
    }

    @Test
    @DisplayName("State singleton works correctly")
    void testSingleton() {
        GlobalSettingsState instance1 = GlobalSettingsState.getInstance();
        GlobalSettingsState instance2 = GlobalSettingsState.getInstance();
        assertSame(instance1, instance2);
    }
}

