package com.checkmarx.intellij.devassist.configuration.mcp;

import com.checkmarx.intellij.devassist.remediation.agent.GenericMcpInstaller;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles MCP cleanup when the Checkmarx plugin is uninstalled.
 * Uses {@link GenericMcpInstaller} to remove Checkmarx MCP entries from ALL registered AI agents.
 */
public final class McpUninstallHandler implements DynamicPluginListener {

    private static final Logger LOG = Logger.getInstance(McpUninstallHandler.class);
    private static final String CHECKMARX_PLUGIN_ID = "com.checkmarx.checkmarx-ast-jetbrains-plugin";
    private static final String CX_DEVASSIST_PLUGIN_ID = "com.checkmarx.devassist-jetbrains-plugin";

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // Only clean up when our plugin is being uninstalled (not updated)
        if (!isUpdate && (CHECKMARX_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())
                || CX_DEVASSIST_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString()))) {
            try {
                boolean removed = GenericMcpInstaller.uninstallFromAllAgents();
                if (removed) {
                    LOG.info("Checkmarx MCP configuration removed from all agents during plugin uninstallation");
                } else {
                    LOG.debug("No Checkmarx MCP configuration found in any agent during plugin uninstallation");
                }
            } catch (Exception ex) {
                LOG.warn("Failed to remove Checkmarx MCP configuration during plugin uninstallation", ex);
            }
        }
    }
}
