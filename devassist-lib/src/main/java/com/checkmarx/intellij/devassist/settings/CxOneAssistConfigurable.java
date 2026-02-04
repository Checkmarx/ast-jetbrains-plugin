package com.checkmarx.intellij.devassist.settings;

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
 * Settings child node under "Checkmarx One" for CxOne Assist realtime features.
 */
public class CxOneAssistConfigurable implements SearchableConfigurable, Configurable.NoScroll {

    private SettingsComponent settingsComponent;

    @Override
    public @NotNull @NonNls String getId() {
        // Place under the same search group; ID should be unique
        return Constants.GLOBAL_SETTINGS_ID + ".assist";
    }

    @Override
    public @Nullable @NonNls String getHelpTopic() {
        return getId();
    }

    @Override
    public @NotNull @Nls String getDisplayName() {
        return Bundle.message(Resource.CXONE_ASSIST_TITLE);
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsComponent = new CxOneAssistComponent();
        return settingsComponent.getMainPanel();
    }

    @Override
    public boolean isModified() {
        return settingsComponent != null && settingsComponent.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        if (settingsComponent != null) {
            settingsComponent.apply();
        }
    }

    @Override
    public void reset() {
        if (settingsComponent != null) {
            settingsComponent.reset();
        }
        SearchableConfigurable.super.reset();
    }
}