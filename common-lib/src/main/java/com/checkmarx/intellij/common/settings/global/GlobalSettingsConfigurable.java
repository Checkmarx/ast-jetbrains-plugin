package com.checkmarx.intellij.common.settings.global;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.SettingsComponent;
import com.checkmarx.intellij.common.utils.Constants;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * {@link SearchableConfigurable} for drawing the plugin's global settings window inside intellij application settings.
 * Implements {@link com.intellij.openapi.options.Configurable.NoScroll} to disable scrolling as all fields should be
 * scrollable on their own.
 */
public class GlobalSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private SettingsComponent settingsComponent;

    @Override
    public @NotNull @NonNls String getId() {
        return Constants.GLOBAL_SETTINGS_ID;
    }

    @Override
    public @Nullable @NonNls String getHelpTopic() {
        return getId();
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
        return Bundle.message(Resource.SETTINGS_TITLE);
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsComponent = new GlobalSettingsComponent();
        return settingsComponent.getMainPanel();
    }

    @Override
    public boolean isModified() {
        return settingsComponent.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        settingsComponent.apply();
    }

    @Override
    public void reset() {
        settingsComponent.reset();
        SearchableConfigurable.super.reset();
    }
}
