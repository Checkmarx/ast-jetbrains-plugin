package com.checkmarx.intellij.cxdevassist.utils;

/**
 * Constants used in the checkmarx developer plugin.
 */
public final class CxDevAssistConstants {

    private CxDevAssistConstants() {
        throw new IllegalStateException("Can not instantiate Utility class");
    }

    public static final String PLUGIN_NAME = "Checkmarx Developer Assist";
    public static final String PLUGIN_CHILD_REALTIME_SETTINGS_ID = "settings.devassist.realtime";
    public static final String FINDINGS_WINDOW_NAME = "Checkmarx Developer Assist Findings";
    public static final String IGNORED_FINDINGS_WINDOW_NAME = "Ignored Findings";
    public static final String DEVASSIST_HELP_LINK = "https://docs.checkmarx.com/en/34965-549323-checkmarx-developer-assist---jetbrains-plugin.html";
}
