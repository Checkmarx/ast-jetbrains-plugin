package com.checkmarx.intellij.common.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.common.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.global.GlobalSettingsState;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handle tenant settings related operations with the wrapper
 */
public class TenantSetting {

    private static final Logger LOG = Logger.getInstance(TenantSetting.class);

    /**
     * Check if the user is currently authenticated.
     *
     * @return true if user is authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        try {
            return GlobalSettingsState.getInstance().isAuthenticated();
        } catch (Exception e) {
            LOG.warn("Failed to check authentication status", e);
            return false;
        }
    }

    /**
     * Check if current tenant has permissions to scan from the IDE
     *
     * @return true if tenant has permissions to scan. false otherwise
     */
    public static boolean isScanAllowed() throws
            IOException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().ideScansEnabled();
    }

    /**
     * Check if AI MCP server is enabled for the current tenant
     *
     * @return true if AI MCP server is enabled, false otherwise
     */
    public static boolean isAiMcpServerEnabled(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState) throws
            IOException,
            CxException,
            InterruptedException {
        LOG.debug("Checking AI MCP server enabled flag using provided credentials");
        return CxWrapperFactory.build(state, sensitiveState).aiMcpServerEnabled();
    }

    /**
     * Get all tenant settings as a Map.
     * Uses the provided GlobalSettingsState and GlobalSettingsSensitiveState for authentication.
     *
     * @param state          GlobalSettingsState object contains current plugin state
     * @param sensitiveState GlobalSettingsSensitiveState object contains encrypted credentials
     * @return Map of tenant setting keys to their values
     */
    public static Map<String, String> getTenantSettingsMap(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState) throws
            IOException,
            CxException,
            InterruptedException {
        LOG.debug("Fetching tenant settings map using provided credentials");
        List<com.checkmarx.ast.tenant.TenantSetting> settings = CxWrapperFactory.build(state, sensitiveState).tenantSettings();
        Map<String, String> settingsMap = new HashMap<>();
        for (com.checkmarx.ast.tenant.TenantSetting setting : settings) {
            settingsMap.put(setting.getKey(), setting.getValue());
        }
        LOG.debug("Fetched tenant settings map using provided credentials: " + settingsMap);
        return settingsMap;
    }

    // Tenant setting keys for license flags - public for use in GlobalSettingsComponent
    public static final String KEY_DEV_ASSIST = "scan.config.plugins.cxdevassist";
    public static final String KEY_ONE_ASSIST = "scan.config.plugins.cxoneassist";
}
