package com.checkmarx.intellij.settings;

import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;

/**
 * Listener interface for when Checkmarx settings are applied
 */
public interface SettingsListener {

    /**
     * Topic to subscribe when using the listener, see:
     * {@link MessageBus#connect()}
     * {@link MessageBusConnection#subscribe(Topic)}
     */
    Topic<SettingsListener> SETTINGS_APPLIED = Topic.create("CxSettingsApplied", SettingsListener.class);

    /**
     * Method called in all subscribing instances when settings are applied
     */
    void settingsApplied();
}
