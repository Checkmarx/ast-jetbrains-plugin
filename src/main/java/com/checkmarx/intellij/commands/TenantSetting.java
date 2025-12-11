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

}
