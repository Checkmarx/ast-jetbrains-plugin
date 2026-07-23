package com.checkmarx.intellij.common.wrapper;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;

import java.io.IOException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

    public static CxWrapper build() throws CxException, IOException {
        return build(GlobalSettingsState.getInstance(), GlobalSettingsSensitiveState.getInstance());
    }

    public static CxWrapper build(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws CxException, IOException {
        final CxConfig.CxConfigBuilder builder = CxConfig.builder();

        String agentName = Constants.JET_BRAINS_AGENT_NAME;
        String pluginVersion = getPluginVersion();

        if (pluginVersion != null && !pluginVersion.isEmpty()) {
            agentName = agentName + "_" + pluginVersion;
        }
        builder.agentName(agentName);
        if(isCredentialExpired(state, sensitiveState)){
            Utils.notifySessionExpired();
            return new CxWrapper(builder.build());
        }
        if (state.isApiKeyEnabled()) {
            builder.apiKey(sensitiveState.getApiKey());
        } else {
            builder.apiKey(sensitiveState.getRefreshToken());
            builder.clientId(Constants.AuthConstants.OAUTH_IDE_CLIENT_ID);
        }
        builder.additionalParameters(state.getAdditionalParameters());
        return new CxWrapper(builder.build());
    }

    /**
     * Retrieves the plugin version from the plugin descriptor.
     * Tries both the Checkmarx AST plugin and DevAssist plugin IDs.
     *
     * @return plugin version string, or empty string if version cannot be determined
     */
    private static String getPluginVersion() {
        final String[] pluginIds = {
            "com.checkmarx.checkmarx-ast-jetbrains-plugin",
            "com.checkmarx.devassist-jetbrains-plugin"
        };

        for (String pluginIdStr : pluginIds) {
            try {
                final PluginId pluginId = PluginId.getId(pluginIdStr);
                final var plugin = PluginManagerCore.getPlugin(pluginId);
                if (plugin != null) {
                    final String version = plugin.getVersion();
                    if (version != null && !version.isEmpty()) {
                        Utils.getLogger(CxWrapperFactory.class).info("Plugin version: " + version);
                        return version;
                    }
                }
            } catch (Exception e) {
                Utils.getLogger(CxWrapperFactory.class).debug("Failed to read plugin version for " + pluginIdStr + ": " + e.getMessage());
            }
        }
        return "";
    }

    /**
     * Checking if credential is expired or not based on login method.
     * If a user is logged in using an API key, then its check API key expiry else checks refresh token expiry.
     * If credentials are expired, then show a session expiration message and publish new state.
     *
     * @param state          GlobalSettingsState object contains current plugin state
     * @param sensitiveState GlobalSettingsSensitiveState object contains encrypted credentials
     * @return true, if credentials expired else false
     */
    private static boolean isCredentialExpired(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState) {
        if (state.isApiKeyEnabled()) {
            return false;
        } else return sensitiveState.isTokenExpired(state.getRefreshTokenExpiry());
    }
}
