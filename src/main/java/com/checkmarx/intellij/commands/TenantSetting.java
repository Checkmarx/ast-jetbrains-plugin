package com.checkmarx.intellij.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;

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
     * Check if the current tenant has Checkmarx Dev Assist license.
     * Returns false if user is not authenticated.
     *
     * @return true if Dev Assist license is enabled, false otherwise (including when not authenticated)
     */
    public static boolean isDevAssistEnabled() throws
            IOException,
            CxException,
            InterruptedException {
        LOG.info("Checking Dev Assist license enabled flag");

        // Check authentication first - don't make API calls if not authenticated
        if (!isAuthenticated()) {
            LOG.info("User not authenticated, returning false for Dev Assist license check");
            return false;
        }

        // TODO: Uncomment when wrapper method is available
        // return CxWrapperFactory.build().devAssistEnabled();
        LOG.info("Dev Assist license check: returning true (TEMPORARY FOR TESTING)");
        return true ; // TEMPORARY: return true for testing promotional panel
    }

    /**
     * Check if the current tenant has Checkmarx One Assist license.
     * Returns false if user is not authenticated.
     *
     * @return true if One Assist license is enabled, false otherwise (including when not authenticated)
     */
    public static boolean isOneAssistEnabled() throws
            IOException,
            CxException,
            InterruptedException {
        LOG.info("Checking One Assist license enabled flag");

        // Check authentication first - don't make API calls if not authenticated
        if (!isAuthenticated()) {
            LOG.info("User not authenticated, returning false for One Assist license check");
            return false;
        }

        // TODO: Uncomment when wrapper method is available
        // return CxWrapperFactory.build().oneAssistEnabled();
        LOG.info("One Assist license check: returning false (API not yet available)");
        return false; // Temporary: return false for authenticated users until API is available
    }

}
