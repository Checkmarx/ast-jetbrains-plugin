package com.checkmarx.intellij.common.settings.global;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;

/**
 * Provides the CxOne Assist settings page only when the user is authenticated and holds a One Assist or Dev Assist license.
 */
public class CxOneAssistConfigurableProvider extends ConfigurableProvider {

    @Override
    public boolean canCreateConfigurable() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        return state.isAuthenticated()
                && (state.isOneAssistLicenseEnabled() || state.isDevAssistLicenseEnabled());
    }

    @Override
    public Configurable createConfigurable() {
        return new CxOneAssistConfigurable();
    }
}
