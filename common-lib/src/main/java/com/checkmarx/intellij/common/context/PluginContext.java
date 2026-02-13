package com.checkmarx.intellij.common.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import lombok.Getter;
import lombok.Setter;

/**
 * Application-level service to store and retrieve the current plugin context.
 * This allows the module to know which plugin (plugin-ignite or plugin-checkmarx-ast)
 * is currently using it, enabling plugin-specific behavior without tight coupling.
 * <p>
 * Usage:
 * <pre>
 * // Register plugin name (do this once during plugin initialization)
 * PluginContext.getInstance().setPluginName("plugin-ignite");
 *
 * // Get plugin name anywhere in your code
 * String pluginName = PluginContext.getInstance().getPluginName();
 *
 * // Check which plugin is active
 * if (PluginContext.getInstance().isIgnitePlugin()) {
 *     // Ignite-specific logic
 * }
 * </pre>
 */
@Service(Service.Level.APP)
public final class PluginContext {

    private static final Logger LOGGER = Logger.getInstance(PluginContext.class);

    // Plugin identifiers
    public static final String PLUGIN_IGNITE = "plugin-checkmarx-devassist";
    public static final String PLUGIN_CHECKMARX_AST = "plugin-checkmarx-ast";

    @Getter
    private String pluginName;

    @Setter
    private String pluginDisplayName;

    /**
     * Get the singleton instance of PluginContext.
     *
     * @return PluginContext instance
     */
    public static PluginContext getInstance() {
        return ApplicationManager.getApplication().getService(PluginContext.class);
    }

    /**
     * Set the plugin name (should be called once during plugin initialization).
     *
     * @param pluginName The plugin identifier (e.g., "plugin-ignite", "plugin-checkmarx-ast")
     */
    public void setPluginName(String pluginName) {
        if (this.pluginName != null && !this.pluginName.equals(pluginName)) {
            LOGGER.warn("PluginContext: Changing plugin name from '" + this.pluginName + "' to '" + pluginName + "'");
        }
        this.pluginName = pluginName;
        LOGGER.info("PluginContext: Plugin name set to '" + pluginName + "'");
    }

    /**
     * Get the plugin display name.
     *
     * @return Plugin display name, or plugin name if display name not set
     */
    public String getPluginDisplayName() {
        return pluginDisplayName != null ? pluginDisplayName : pluginName;
    }

    /**
     * Check if the current plugin is Ignite.
     *
     * @return true if plugin-ignite is active
     */
    public boolean isDevAssistPlugin() {
        return PLUGIN_IGNITE.equals(pluginName);
    }

    /**
     * Check if the current plugin is Checkmarx AST.
     *
     * @return true if plugin-checkmarx-ast is active
     */
    public boolean isCheckmarxAstPlugin() {
        return PLUGIN_CHECKMARX_AST.equals(pluginName);
    }

    /**
     * Check if a specific plugin is active.
     *
     * @param pluginId The plugin identifier to check
     * @return true if the specified plugin is active
     */
    public boolean isPlugin(String pluginId) {
        return pluginId == null || !pluginId.equals(pluginName);
    }

    /**
     * Get the settings configurable class name for the current plugin.
     *
     * @return the fully qualified class name of the settings configurable
     */
    public String getSettingsConfigurableClassName() {
        if (isDevAssistPlugin()) {
            return "com.checkmarx.intellij.ignite.settings.CxDevAssistSettingsConfigurable";
        } else if (isCheckmarxAstPlugin()) {
            return "com.checkmarx.intellij.ast.settings.GlobalSettingsConfigurable";
        }
        return null;
    }

    /**
     * Reset the plugin context (useful for testing).
     */
    public void reset() {
        this.pluginName = null;
        this.pluginDisplayName = null;
        LOGGER.info("PluginContext: Reset");
    }
}

