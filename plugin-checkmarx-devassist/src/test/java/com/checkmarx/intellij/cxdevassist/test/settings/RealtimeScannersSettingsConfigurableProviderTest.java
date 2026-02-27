package com.checkmarx.intellij.cxdevassist.test.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsConfigurable;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsConfigurableProvider;
import com.intellij.openapi.options.Configurable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeScannersSettingsConfigurableProviderTest {

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
    @DisplayName("Provider can create configurable")
    void testCreateConfigurable() {
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        Configurable configurable = provider.createConfigurable();
        assertNotNull(configurable);
    }

    @Test
    @DisplayName("Created configurable is correct type")
    void testConfigurableType() {
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        Configurable configurable = provider.createConfigurable();
        assertInstanceOf(RealtimeScannersSettingsConfigurable.class, configurable);
    }

    @Test
    @DisplayName("Can create configurable when authenticated and licensed")
    void testCanCreateConfigurable_Authenticated() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        when(mockGlobalState.isDevAssistLicenseEnabled()).thenReturn(true);
        
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        assertTrue(provider.canCreateConfigurable());
    }

    @Test
    @DisplayName("Cannot create configurable when not authenticated")
    void testCanCreateConfigurable_NotAuthenticated() {
        when(mockGlobalState.isAuthenticated()).thenReturn(false);
        
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        assertFalse(provider.canCreateConfigurable());
    }

    @Test
    @DisplayName("Can create with OneAssist license")
    void testCanCreateConfigurable_OneAssist() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        when(mockGlobalState.isOneAssistLicenseEnabled()).thenReturn(true);
        
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        assertTrue(provider.canCreateConfigurable());
    }

    @Test
    @DisplayName("Multiple providers create independent configurables")
    void testMultipleProviders() {
        RealtimeScannersSettingsConfigurableProvider provider1 = new RealtimeScannersSettingsConfigurableProvider();
        RealtimeScannersSettingsConfigurableProvider provider2 = new RealtimeScannersSettingsConfigurableProvider();
        
        Configurable config1 = provider1.createConfigurable();
        Configurable config2 = provider2.createConfigurable();
        
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Created configurable has correct display name")
    void testConfigurableDisplayName() {
        RealtimeScannersSettingsConfigurableProvider provider = new RealtimeScannersSettingsConfigurableProvider();
        Configurable configurable = provider.createConfigurable();
        assertEquals("Realtime Scanners", configurable.getDisplayName());
    }

    @Test
    @DisplayName("Provider instantiation succeeds")
    void testProviderInstantiation() {
        assertDoesNotThrow(() -> new RealtimeScannersSettingsConfigurableProvider());
    }

    @Test
    @DisplayName("Global state singleton works")
    void testGlobalStateSingleton() {
        GlobalSettingsState state1 = GlobalSettingsState.getInstance();
        GlobalSettingsState state2 = GlobalSettingsState.getInstance();
        assertSame(state1, state2);
    }
}

