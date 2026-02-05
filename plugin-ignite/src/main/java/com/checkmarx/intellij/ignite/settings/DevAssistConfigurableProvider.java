package com.checkmarx.intellij.ignite.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;

/**
 * Provides the Dev Assist settings page only when the user is authenticated and holds a Dev Assist license.
 */
public class DevAssistConfigurableProvider extends ConfigurableProvider {

    @Override
    public boolean canCreateConfigurable() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        return state.isAuthenticated()
                && (state.isOneAssistLicenseEnabled() || state.isDevAssistLicenseEnabled());
    }

    @Override
    public Configurable createConfigurable() {
        return new DevAssistConfigurable();
    }
}
