package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.CxOneAssistConfigurable;
import com.checkmarx.intellij.ast.settings.CxOneAssistConfigurableProvider;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.intellij.openapi.options.Configurable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class CxOneAssistConfigurableProviderTest {

    @Test
    void canCreateConfigurable_WhenAuthenticatedWithLicense_ReturnsTrue() {
        CxOneAssistConfigurableProvider provider = new CxOneAssistConfigurableProvider();
        GlobalSettingsState state = Mockito.mock(GlobalSettingsState.class);
        Mockito.when(state.isAuthenticated()).thenReturn(true);
        Mockito.when(state.isOneAssistLicenseEnabled()).thenReturn(false);
        Mockito.when(state.isDevAssistLicenseEnabled()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> mocked = Mockito.mockStatic(GlobalSettingsState.class)) {
            mocked.when(GlobalSettingsState::getInstance).thenReturn(state);
            assertTrue(provider.canCreateConfigurable());
        }
    }

    @Test
    void canCreateConfigurable_WhenNotLicensedOrAuthenticated_ReturnsFalse() {
        CxOneAssistConfigurableProvider provider = new CxOneAssistConfigurableProvider();
        GlobalSettingsState state = Mockito.mock(GlobalSettingsState.class);
        Mockito.when(state.isAuthenticated()).thenReturn(false);
        Mockito.when(state.isOneAssistLicenseEnabled()).thenReturn(false);
        Mockito.when(state.isDevAssistLicenseEnabled()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> mocked = Mockito.mockStatic(GlobalSettingsState.class)) {
            mocked.when(GlobalSettingsState::getInstance).thenReturn(state);
            assertFalse(provider.canCreateConfigurable());
        }
    }

    @Test
    void createConfigurable_ReturnsNewInstance() {
        CxOneAssistConfigurableProvider provider = new CxOneAssistConfigurableProvider();
        Configurable configurable = provider.createConfigurable();
        assertNotNull(configurable);
        assertTrue(configurable instanceof CxOneAssistConfigurable);
    }
}

