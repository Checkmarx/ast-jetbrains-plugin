package com.checkmarx.intellij.ignite.utils;

/**
 * Constants used in the ignite plugin.
 */
public final class IgniteConstants {

    private IgniteConstants() {
        throw new IllegalStateException("Can not instantiate Utility class");
    }

    public static final String PLUGIN_NAME = "Checkmarx Developer Assist";
    public static final String PLUGIN_PARENT_SETTINGS_ID = "settings.ignite";
    public static final String PLUGIN_CHILD_REALTIME_SETTINGS_ID = "settings.ignite.realtime";

    public static final String FINDINGS_WINDOW_NAME = "Checkmarx Developer Assist Findings";
    public static final String FINDINGS_WINDOW_TOOLBAR_GROUP_ID = "VulnerabilityToolbarGroup";

    public static final String IGNORED_FINDINGS_WINDOW_NAME = "Ignored Findings";
}
