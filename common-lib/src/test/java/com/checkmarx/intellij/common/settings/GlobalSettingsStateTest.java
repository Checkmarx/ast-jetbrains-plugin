package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalSettingsStateTest {

    @Mock
    private Application mockApplication;
    
    @Mock
    private FilterProviderRegistry mockFilterProviderRegistry;
    
    @Mock
    private Filterable mockFilterable;
    
    private GlobalSettingsState globalSettingsState;
    private MockedStatic<FilterProviderRegistry> mockedFilterProviderRegistry;

    @BeforeEach
    void setUp() {
        // Set up the static mock before creating GlobalSettingsState instances
        mockedFilterProviderRegistry = mockStatic(FilterProviderRegistry.class);
        mockedFilterProviderRegistry.when(FilterProviderRegistry::getInstance).thenReturn(mockFilterProviderRegistry);
        
        Set<Filterable> defaultFilters = new HashSet<>();
        defaultFilters.add(mockFilterable);
        when(mockFilterProviderRegistry.getDefaultFilters()).thenReturn(defaultFilters);
        
        globalSettingsState = new GlobalSettingsState();
    }

    @AfterEach
    void tearDown() {
        if (mockedFilterProviderRegistry != null) {
            mockedFilterProviderRegistry.close();
        }
    }

    @Test
    void testGetInstance() {
        try (MockedStatic<ApplicationManager> mockedApplicationManager = mockStatic(ApplicationManager.class)) {
            mockedApplicationManager.when(ApplicationManager::getApplication).thenReturn(mockApplication);
            when(mockApplication.getService(GlobalSettingsState.class)).thenReturn(globalSettingsState);
            
            GlobalSettingsState result = GlobalSettingsState.getInstance();
            
            assertEquals(globalSettingsState, result);
        }
    }

    @Test
    void testGetState() {
        GlobalSettingsState result = globalSettingsState.getState();
        
        assertEquals(globalSettingsState, result);
    }

    @Test
    void testGetFilters_WithValidFilters() {
        Set<Filterable> validFilters = new HashSet<>();
        validFilters.add(mockFilterable);
        
        globalSettingsState.setFilters(validFilters);
        
        Set<Filterable> result = globalSettingsState.getFilters();
        
        assertEquals(validFilters, result);
    }

    @Test
    void testGetFilters_WithEmptyFilters() {
        Set<Filterable> emptyFilters = new HashSet<>();
        Set<Filterable> defaultFilters = new HashSet<>();
        defaultFilters.add(mockFilterable);
        
        globalSettingsState.setFilters(emptyFilters);
        when(mockFilterProviderRegistry.getDefaultFilters()).thenReturn(defaultFilters);
        
        Set<Filterable> result = globalSettingsState.getFilters();
        
        assertEquals(defaultFilters, result);
    }

    @Test
    void testGetFilters_WithInvalidFilters() {
        Set<Filterable> invalidFilters = new HashSet<>();
        invalidFilters.add(null);
        Set<Filterable> defaultFilters = new HashSet<>();
        defaultFilters.add(mockFilterable);
        
        globalSettingsState.setFilters(invalidFilters);
        when(mockFilterProviderRegistry.getDefaultFilters()).thenReturn(defaultFilters);
        
        Set<Filterable> result = globalSettingsState.getFilters();
        
        assertEquals(defaultFilters, result);
    }

    @Test
    void testLoadState() {
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setBaseUrl("https://test.com");
        newState.setTenant("test-tenant");
        newState.setAuthenticated(true);
        
        globalSettingsState.loadState(newState);
        
        assertEquals("https://test.com", globalSettingsState.getBaseUrl());
        assertEquals("test-tenant", globalSettingsState.getTenant());
        assertTrue(globalSettingsState.isAuthenticated());
    }

    @Test
    void testLoadState_WithInvalidFilters() {
        Set<Filterable> defaultFilters = new HashSet<>();
        defaultFilters.add(mockFilterable);
        
        when(mockFilterProviderRegistry.getDefaultFilters()).thenReturn(defaultFilters);
        when(mockFilterProviderRegistry.hasProvider()).thenReturn(true);
        
        GlobalSettingsState newState = new GlobalSettingsState();
        Set<Filterable> invalidFilters = new HashSet<>();
        invalidFilters.add(null);
        newState.setFilters(invalidFilters);
        
        globalSettingsState.loadState(newState);
        
        // After loadState, getFilters() will lazily resolve and replace invalid filters with defaults
        Set<Filterable> result = globalSettingsState.getFilters();
        assertEquals(defaultFilters, result);
    }

    @Test
    void testApply() {
        GlobalSettingsState newState = new GlobalSettingsState();
        newState.setBaseUrl("https://test.com");
        newState.setTenant("test-tenant");
        newState.setAuthenticated(true);
        
        globalSettingsState.apply(newState);
        
        assertEquals("https://test.com", globalSettingsState.getBaseUrl());
        assertEquals("test-tenant", globalSettingsState.getTenant());
        assertTrue(globalSettingsState.isAuthenticated());
    }

    @Test
    void testUserPreferences() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        assertTrue(globalSettingsState.getUserPreferencesSet());
        assertTrue(globalSettingsState.getUserPrefAscaRealtime());
        assertFalse(globalSettingsState.getUserPrefOssRealtime());
        assertTrue(globalSettingsState.getUserPrefSecretDetectionRealtime());
        assertFalse(globalSettingsState.getUserPrefContainersRealtime());
        assertTrue(globalSettingsState.getUserPrefIacRealtime());
    }

    @Test
    void testApplyUserPreferencesToRealtimeSettings_NoPreferencesSet() {
        boolean result = globalSettingsState.applyUserPreferencesToRealtimeSettings();
        
        assertFalse(result);
    }

    @Test
    void testApplyUserPreferencesToRealtimeSettings_WithPreferencesSet() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        boolean result = globalSettingsState.applyUserPreferencesToRealtimeSettings();
        
        assertTrue(result);
        assertTrue(globalSettingsState.isAscaRealtime());
        assertFalse(globalSettingsState.isOssRealtime());
        assertTrue(globalSettingsState.isSecretDetectionRealtime());
        assertFalse(globalSettingsState.isContainersRealtime());
        assertTrue(globalSettingsState.isIacRealtime());
    }

    @Test
    void testApplyUserPreferencesToRealtimeSettings_NoChangesNeeded() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        globalSettingsState.setAscaRealtime(true);
        globalSettingsState.setOssRealtime(false);
        globalSettingsState.setSecretDetectionRealtime(true);
        globalSettingsState.setContainersRealtime(false);
        globalSettingsState.setIacRealtime(true);
        
        boolean result = globalSettingsState.applyUserPreferencesToRealtimeSettings();
        
        assertFalse(result);
    }

    @Test
    void testSaveCurrentSettingsAsUserPreferences() {
        globalSettingsState.setAscaRealtime(true);
        globalSettingsState.setOssRealtime(false);
        globalSettingsState.setSecretDetectionRealtime(true);
        globalSettingsState.setContainersRealtime(false);
        globalSettingsState.setIacRealtime(true);
        
        globalSettingsState.saveCurrentSettingsAsUserPreferences();
        
        assertTrue(globalSettingsState.getUserPreferencesSet());
        assertTrue(globalSettingsState.getUserPrefAscaRealtime());
        assertFalse(globalSettingsState.getUserPrefOssRealtime());
        assertTrue(globalSettingsState.getUserPrefSecretDetectionRealtime());
        assertFalse(globalSettingsState.getUserPrefContainersRealtime());
        assertTrue(globalSettingsState.getUserPrefIacRealtime());
    }

    @Test
    void testHasCustomUserPreferences_NoPreferencesSet() {
        boolean result = globalSettingsState.hasCustomUserPreferences();
        
        assertFalse(result);
    }

    @Test
    void testHasCustomUserPreferences_AllEnabled() {
        globalSettingsState.setUserPreferences(true, true, true, true, true);
        
        boolean result = globalSettingsState.hasCustomUserPreferences();
        
        assertFalse(result);
    }

    @Test
    void testHasCustomUserPreferences_SomeDisabled() {
        globalSettingsState.setUserPreferences(true, false, true, true, true);
        
        boolean result = globalSettingsState.hasCustomUserPreferences();
        
        assertTrue(result);
    }

    @Test
    void testIsAscaRealtime() {
        globalSettingsState.setAscaRealtime(true);
        
        assertTrue(globalSettingsState.isAscaRealtime());
    }

    @Test
    void testSetAscaRealtime() {
        globalSettingsState.setAscaRealtime(true);
        
        assertTrue(globalSettingsState.isAscaRealtime());
    }

    @Test
    void testGetUserPreferencesSet() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        assertTrue(globalSettingsState.getUserPreferencesSet());
    }

    @Test
    void testGetUserPrefAscaRealtime() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        assertTrue(globalSettingsState.getUserPrefAscaRealtime());
    }

    @Test
    void testGetUserPrefOssRealtime() {
        globalSettingsState.setUserPreferences(true, true, true, false, true);
        
        assertTrue(globalSettingsState.getUserPrefOssRealtime());
    }

    @Test
    void testGetUserPrefSecretDetectionRealtime() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        assertTrue(globalSettingsState.getUserPrefSecretDetectionRealtime());
    }

    @Test
    void testGetUserPrefContainersRealtime() {
        globalSettingsState.setUserPreferences(true, false, true, true, true);
        
        assertTrue(globalSettingsState.getUserPrefContainersRealtime());
    }

    @Test
    void testGetUserPrefIacRealtime() {
        globalSettingsState.setUserPreferences(true, false, true, false, true);
        
        assertTrue(globalSettingsState.getUserPrefIacRealtime());
    }

    @Test
    void testIsDevAssistLicenseEnabled() {
        globalSettingsState.setDevAssistLicenseEnabled(true);
        
        assertTrue(globalSettingsState.isDevAssistLicenseEnabled());
    }

    @Test
    void testSetDevAssistLicenseEnabled() {
        globalSettingsState.setDevAssistLicenseEnabled(true);
        
        assertTrue(globalSettingsState.isDevAssistLicenseEnabled());
    }

    @Test
    void testIsOneAssistLicenseEnabled() {
        globalSettingsState.setOneAssistLicenseEnabled(true);
        
        assertTrue(globalSettingsState.isOneAssistLicenseEnabled());
    }

    @Test
    void testSetOneAssistLicenseEnabled() {
        globalSettingsState.setOneAssistLicenseEnabled(true);
        
        assertTrue(globalSettingsState.isOneAssistLicenseEnabled());
    }

    @Test
    void testDefaultValues() {
        GlobalSettingsState newState = new GlobalSettingsState();
        
        assertEquals("", newState.getValidationMessage());
        assertEquals("", newState.getAdditionalParameters());
        assertFalse(newState.isAsca());
        assertFalse(newState.isApiKeyEnabled());
        assertFalse(newState.isAuthenticated());
        assertTrue(newState.isLastValidationSuccess());
        assertNull(newState.getRefreshTokenExpiry());
        assertNull(newState.getValidationExpiry());
        assertNotNull(newState.getFilters());
        assertEquals("", newState.getBaseUrl());
        assertEquals("", newState.getTenant());
        assertFalse(newState.isValidationInProgress());
        assertFalse(newState.isOssRealtime());
        assertFalse(newState.isSecretDetectionRealtime());
        assertFalse(newState.isContainersRealtime());
        assertFalse(newState.isIacRealtime());
        assertEquals("docker", newState.getContainersTool());
        assertFalse(newState.isMcpEnabled());
        assertFalse(newState.isMcpStatusChecked());
        assertFalse(newState.isWelcomeShown());
        assertFalse(newState.isDevAssistLicenseEnabled());
        assertFalse(newState.isOneAssistLicenseEnabled());
        assertFalse(newState.isAscaRealtime());
        assertFalse(newState.getUserPreferencesSet());
        assertFalse(newState.getUserPrefAscaRealtime());
        assertFalse(newState.getUserPrefOssRealtime());
        assertFalse(newState.getUserPrefSecretDetectionRealtime());
        assertFalse(newState.getUserPrefContainersRealtime());
        assertFalse(newState.getUserPrefIacRealtime());
    }

    @Test
    void testSettersAndGetters() {
        globalSettingsState.setValidationMessage("test message");
        assertEquals("test message", globalSettingsState.getValidationMessage());
        
        globalSettingsState.setAdditionalParameters("test params");
        assertEquals("test params", globalSettingsState.getAdditionalParameters());
        
        globalSettingsState.setAsca(true);
        assertTrue(globalSettingsState.isAsca());
        
        globalSettingsState.setApiKeyEnabled(true);
        assertTrue(globalSettingsState.isApiKeyEnabled());
        
        globalSettingsState.setAuthenticated(true);
        assertTrue(globalSettingsState.isAuthenticated());
        
        globalSettingsState.setLastValidationSuccess(false);
        assertFalse(globalSettingsState.isLastValidationSuccess());
        
        globalSettingsState.setRefreshTokenExpiry("2023-12-31");
        assertEquals("2023-12-31", globalSettingsState.getRefreshTokenExpiry());
        
        globalSettingsState.setValidationExpiry("2023-12-31");
        assertEquals("2023-12-31", globalSettingsState.getValidationExpiry());
        
        globalSettingsState.setBaseUrl("https://test.com");
        assertEquals("https://test.com", globalSettingsState.getBaseUrl());
        
        globalSettingsState.setTenant("test-tenant");
        assertEquals("test-tenant", globalSettingsState.getTenant());
        
        globalSettingsState.setValidationInProgress(true);
        assertTrue(globalSettingsState.isValidationInProgress());
        
        globalSettingsState.setOssRealtime(true);
        assertTrue(globalSettingsState.isOssRealtime());
        
        globalSettingsState.setSecretDetectionRealtime(true);
        assertTrue(globalSettingsState.isSecretDetectionRealtime());
        
        globalSettingsState.setContainersRealtime(true);
        assertTrue(globalSettingsState.isContainersRealtime());
        
        globalSettingsState.setIacRealtime(true);
        assertTrue(globalSettingsState.isIacRealtime());
        
        globalSettingsState.setContainersTool("podman");
        assertEquals("podman", globalSettingsState.getContainersTool());
        
        globalSettingsState.setMcpEnabled(true);
        assertTrue(globalSettingsState.isMcpEnabled());
        
        globalSettingsState.setMcpStatusChecked(true);
        assertTrue(globalSettingsState.isMcpStatusChecked());
        
        globalSettingsState.setWelcomeShown(true);
        assertTrue(globalSettingsState.isWelcomeShown());
    }

    @Test
    void testIsValidFilterCollection_PrivateMethod() {
        // Since isValidFilterCollection is private, we test it indirectly through getFilters()
        Set<Filterable> validFilters = new HashSet<>();
        validFilters.add(mockFilterable);
        
        globalSettingsState.setFilters(validFilters);
        
        // This should not trigger filter replacement since filters are valid
        Set<Filterable> result = globalSettingsState.getFilters();
        assertEquals(validFilters, result);
    }

    @Test
    void testGetFilters_EdgeCaseWithNullFilters() {
        // Test the case where filters set contains only null elements
        Set<Filterable> nullFilters = new HashSet<>();
        nullFilters.add(null);
        nullFilters.add(null);
        
        globalSettingsState.setFilters(nullFilters);
        
        Set<Filterable> result = globalSettingsState.getFilters();
        
        // Should return default filters since all elements are null
        assertNotNull(result);
        assertTrue(result.contains(mockFilterable));
    }

    @Test
    void testApplyUserPreferencesToRealtimeSettings_EdgeCases() {
        // Test with all preferences false
        globalSettingsState.setUserPreferences(false, false, false, false, false);
        
        // First set all realtime settings to true to ensure they get changed
        globalSettingsState.setAscaRealtime(true);
        globalSettingsState.setOssRealtime(true);
        globalSettingsState.setSecretDetectionRealtime(true);
        globalSettingsState.setContainersRealtime(true);
        globalSettingsState.setIacRealtime(true);
        
        boolean result = globalSettingsState.applyUserPreferencesToRealtimeSettings();
        
        assertTrue(result);
        assertFalse(globalSettingsState.isAscaRealtime());
        assertFalse(globalSettingsState.isOssRealtime());
        assertFalse(globalSettingsState.isSecretDetectionRealtime());
        assertFalse(globalSettingsState.isContainersRealtime());
        assertFalse(globalSettingsState.isIacRealtime());
    }

    @Test
    void testUserPreferences_MixedValues() {
        // Test various combinations of user preferences
        globalSettingsState.setUserPreferences(false, true, false, true, false);
        
        assertTrue(globalSettingsState.getUserPreferencesSet());
        assertFalse(globalSettingsState.getUserPrefAscaRealtime());
        assertTrue(globalSettingsState.getUserPrefOssRealtime());
        assertFalse(globalSettingsState.getUserPrefSecretDetectionRealtime());
        assertTrue(globalSettingsState.getUserPrefContainersRealtime());
        assertFalse(globalSettingsState.getUserPrefIacRealtime());
    }

    @Test
    void testLicenseSettings() {
        // Test both license settings together
        globalSettingsState.setDevAssistLicenseEnabled(true);
        globalSettingsState.setOneAssistLicenseEnabled(false);
        
        assertTrue(globalSettingsState.isDevAssistLicenseEnabled());
        assertFalse(globalSettingsState.isOneAssistLicenseEnabled());
        
        // Flip them
        globalSettingsState.setDevAssistLicenseEnabled(false);
        globalSettingsState.setOneAssistLicenseEnabled(true);
        
        assertFalse(globalSettingsState.isDevAssistLicenseEnabled());
        assertTrue(globalSettingsState.isOneAssistLicenseEnabled());
    }

    @Test
    void testRealtimeSettings_AllEnabled() {
        // Test setting all realtime settings to true
        globalSettingsState.setAscaRealtime(true);
        globalSettingsState.setOssRealtime(true);
        globalSettingsState.setSecretDetectionRealtime(true);
        globalSettingsState.setContainersRealtime(true);
        globalSettingsState.setIacRealtime(true);
        
        assertTrue(globalSettingsState.isAscaRealtime());
        assertTrue(globalSettingsState.isOssRealtime());
        assertTrue(globalSettingsState.isSecretDetectionRealtime());
        assertTrue(globalSettingsState.isContainersRealtime());
        assertTrue(globalSettingsState.isIacRealtime());
    }

    @Test
    void testContainersToolSettings() {
        // Test different container tools
        assertEquals("docker", globalSettingsState.getContainersTool());
        
        globalSettingsState.setContainersTool("podman");
        assertEquals("podman", globalSettingsState.getContainersTool());
        
        globalSettingsState.setContainersTool("containerd");
        assertEquals("containerd", globalSettingsState.getContainersTool());
    }

    @Test
    void testValidationSettings() {
        // Test validation-related settings
        globalSettingsState.setValidationMessage("Test validation message");
        globalSettingsState.setValidationInProgress(true);
        globalSettingsState.setLastValidationSuccess(false);
        globalSettingsState.setValidationExpiry("2023-12-31T23:59:59");
        
        assertEquals("Test validation message", globalSettingsState.getValidationMessage());
        assertTrue(globalSettingsState.isValidationInProgress());
        assertFalse(globalSettingsState.isLastValidationSuccess());
        assertEquals("2023-12-31T23:59:59", globalSettingsState.getValidationExpiry());
    }

    @Test
    void testAuthenticationSettings() {
        // Test authentication-related settings
        globalSettingsState.setAuthenticated(true);
        globalSettingsState.setApiKeyEnabled(true);
        globalSettingsState.setRefreshTokenExpiry("2023-12-31T23:59:59");
        
        assertTrue(globalSettingsState.isAuthenticated());
        assertTrue(globalSettingsState.isApiKeyEnabled());
        assertEquals("2023-12-31T23:59:59", globalSettingsState.getRefreshTokenExpiry());
    }

    @Test
    void testMcpAndWelcomeSettings() {
        // Test MCP and welcome screen settings
        globalSettingsState.setMcpEnabled(true);
        globalSettingsState.setMcpStatusChecked(true);
        globalSettingsState.setWelcomeShown(true);
        
        assertTrue(globalSettingsState.isMcpEnabled());
        assertTrue(globalSettingsState.isMcpStatusChecked());
        assertTrue(globalSettingsState.isWelcomeShown());
    }
}
