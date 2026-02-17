package com.checkmarx.intellij.common.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import lombok.Getter;
import lombok.Setter;

import static java.lang.String.format;

/**
 * Application-level service to store and retrieve the current plugin context.
 * This allows the module to know which plugin (plugin-checkmarx-devassist or plugin-checkmarx-ast)
 * is currently using it, enabling plugin-specific behavior without tight coupling.
 * <p>
 * Usage:
 * <pre>
 * // Register plugin name (do this once during plugin initialization)
 * PluginContext.getInstance().setPluginName("plugin-checkmarx-devassist");
 *
 * // Get plugin name anywhere in your code
 * String pluginName = PluginContext.getInstance().getPluginName();
 *
 * </pre>
 */
@Service(Service.Level.APP)
public final class PluginContext {

    private static final Logger LOGGER = Logger.getInstance(PluginContext.class);

    // Plugin identifiers
    public static final String PLUGIN_CHECKMARX_DEVASSIST = "plugin-checkmarx-devassist";
    public static final String PLUGIN_CHECKMARX_AST = "plugin-checkmarx-ast";

    @Getter
    private volatile String pluginName;

    @Setter
    private volatile String pluginDisplayName;

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
     * @param pluginName The plugin identifier (e.g., "plugin-checkmarx-devassist", "plugin-checkmarx-ast")
     */
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
        LOGGER.debug(format("PluginContext: Setting plugin name: %s", pluginName));
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
     * Check if the current plugin is Checkmarx Developer Assist.
     *
     * @return true if plugin-checkmarx-devassist is active
     */
    public boolean isDevAssistPlugin() {
        return PLUGIN_CHECKMARX_DEVASSIST.equals(pluginName);
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
            return "com.checkmarx.intellij.cxdevassist.settings.CxDevAssistSettingsConfigurable";
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
        LOGGER.debug("PluginContext: Resetting plugin context.");
    }
}

