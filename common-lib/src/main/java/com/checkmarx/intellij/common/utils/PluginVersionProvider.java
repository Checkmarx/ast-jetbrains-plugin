package com.checkmarx.intellij.common.utils;

import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the plugin version at runtime.
 * Version is generated at build time and embedded in version.properties.
 * This avoids using internal IntelliJ APIs like PluginManagerCore.
 */
public class PluginVersionProvider {
    private static final String VERSION;

    static {
        String version = "";
        try (InputStream is = PluginVersionProvider.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("plugin.version", "");
            }
        } catch (Exception e) {
            // Silently fail if version.properties is not found or cannot be read
            Utils.getLogger(PluginVersionProvider.class).debug("Failed to load plugin version from version.properties: " + e.getMessage());
        }
        VERSION = version;
    }

    public static String getPluginVersion() {
        return VERSION;
    }
}
