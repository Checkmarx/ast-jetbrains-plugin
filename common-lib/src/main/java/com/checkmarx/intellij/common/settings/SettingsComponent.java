package com.checkmarx.intellij.common.settings;

import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
 * Interface for a settings component.
 * The Main Panel is what is drawn in the intellij window.
 */
public interface SettingsComponent {

    /**
     * Supply a Main Panel to draw inside intellij
     *
     * @return the panel
     */
    JPanel getMainPanel();

    /**
     * Checks for changes in each relevant field when compared to the stored settings
     *
     * @return whether there are changes
     */
    boolean isModified();

    /**
     * Validates and applies changes to the component's settings.
     *
     * @throws ConfigurationException when validation fails
     */
    void apply() throws ConfigurationException;

    /**
     * Reset field's values to the stored values, reverting all changes.
     */
    void reset();
}
