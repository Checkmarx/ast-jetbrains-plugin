package com.checkmarx.intellij.common.devassist.configuration.mcp;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles MCP cleanup when the Checkmarx plugin is uninstalled.
 * Calls the existing McpSettingsInjector.uninstallFromCopilot() method.
 */
public final class McpUninstallHandler implements DynamicPluginListener {

    private static final Logger LOG = Logger.getInstance(McpUninstallHandler.class);
    private static final String CHECKMARX_PLUGIN_ID = "com.checkmarx.checkmarx-ast-jetbrains-plugin";

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // Only clean up when our plugin is being uninstalled (not updated)
        if (!isUpdate && CHECKMARX_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
            try {
                // Call the existing uninstall method directly
                boolean removed = McpSettingsInjector.uninstallFromCopilot();
                if (removed) {
                    LOG.info("Checkmarx MCP configuration removed during plugin uninstallation");
                } else {
                    LOG.debug("No Checkmarx MCP configuration found during plugin uninstallation");
                }
            } catch (Exception ex) {
                LOG.warn("Failed to remove Checkmarx MCP configuration during plugin uninstallation", ex);
            }
        }
    }
}
