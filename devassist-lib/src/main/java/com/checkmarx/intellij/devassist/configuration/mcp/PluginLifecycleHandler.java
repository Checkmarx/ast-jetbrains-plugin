package com.checkmarx.intellij.devassist.configuration.mcp;

import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles plugin lifecycle events for Checkmarx plugins.
 * <p>
 * On uninstall (not update) it:
 * <ol>
 *   <li>Clears the persisted authentication session so that OAuth credentials
 *       do not carry over when the user later installs the other Checkmarx plugin.</li>
 *   <li>Removes the Checkmarx MCP server entry from the Copilot configuration.</li>
 * </ol>
 */
public final class PluginLifecycleHandler implements DynamicPluginListener {

    private static final Logger LOG = Logger.getInstance(PluginLifecycleHandler.class);
    private static final String CHECKMARX_PLUGIN_ID = "com.checkmarx.checkmarx-ast-jetbrains-plugin";
    private static final String CX_DEVASSIST_PLUGIN_ID = "com.checkmarx.devassist-jetbrains-plugin";

    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        // Only clean up when our plugin is being uninstalled (not updated)
        if (!isUpdate && (CHECKMARX_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())
                || CX_DEVASSIST_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString()))) {
            clearAuthSession();
            removeMcpConfiguration();
        }
    }

    /**
     * Clears the persisted authentication session.
     * <p>
     * This prevents an OAuth login from carrying over when the user installs the
     * other Checkmarx plugin (e.g. uninstalling checkmarx-ast and installing devassist,
     * or vice-versa), where the retained OAuth session would be invalid / disallowed.
     */
    public void clearAuthSession() {
        try {
            GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
            if (settingsState != null) {
                settingsState.setAuthenticated(false);
                LOG.info("Checkmarx authentication session cleared during plugin uninstallation");
            }

            GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
            if (sensitiveState != null) {
                sensitiveState.deleteRefreshToken();
                LOG.info("Checkmarx OAuth refresh token deleted during plugin uninstallation");
            }

            // Force-save application settings to disk immediately so the authenticated=false
            // change is written to cx_global_settings.xml before the IDE restarts.
            // Without this, the in-memory change would be lost and the new plugin would
            // still read authenticated=true from the persisted XML file on next startup.
            ApplicationManager.getApplication().saveSettings();
            LOG.info("Application settings saved to disk after clearing Checkmarx auth session");
        } catch (Exception ex) {
            LOG.warn("Failed to clear Checkmarx authentication session during plugin uninstallation", ex);
        }
    }

    /**
     * Removes the Checkmarx MCP server entry from the Copilot configuration.
     */
    private void removeMcpConfiguration() {
        try {
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

