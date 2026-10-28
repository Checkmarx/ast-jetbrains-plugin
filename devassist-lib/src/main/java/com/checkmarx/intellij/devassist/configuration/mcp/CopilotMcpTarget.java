package com.checkmarx.intellij.devassist.configuration.mcp;

import java.nio.file.Path;

/**
 * {@link McpAgentTarget} adapter over the existing static Copilot methods on {@link McpSettingsInjector}.
 */
public final class CopilotMcpTarget implements McpAgentTarget {

    @Override
    public boolean install(String credential) throws Exception {
        return McpSettingsInjector.installForCopilot(credential);
    }

    @Override
    public boolean uninstall() throws Exception {
        return McpSettingsInjector.uninstallFromCopilot();
    }

    @Override
    public Path getConfigPath() {
        return McpSettingsInjector.getMcpJsonPath();
    }

    // No dedicated settings page for MCP - Copilot has no such page, so the raw
    // config file (getConfigPath()) is opened instead. Uses the interface default (empty).
}
