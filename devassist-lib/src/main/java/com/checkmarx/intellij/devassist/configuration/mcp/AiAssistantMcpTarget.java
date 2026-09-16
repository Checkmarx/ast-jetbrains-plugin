package com.checkmarx.intellij.devassist.configuration.mcp;

import com.checkmarx.intellij.devassist.remediation.AiAssistantIntegration;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link McpAgentTarget} adapter over the existing static AI Assistant methods on {@link McpSettingsInjector}.
 */
public final class AiAssistantMcpTarget implements McpAgentTarget {

    @Override
    public boolean install(String credential) throws Exception {
        return McpSettingsInjector.installForAiAssistant(credential);
    }

    @Override
    public boolean uninstall() throws Exception {
        return McpSettingsInjector.uninstallFromAiAssistant();
    }

    @Override
    public Path getConfigPath() {
        return McpSettingsInjector.getAiAssistantMcpJsonPath();
    }

    @Override
    public Optional<String> getSettingsConfigurableId() {
        return Optional.of(AiAssistantIntegration.AI_ASSISTANT_MCP_CONFIGURABLE_ID);
    }
}
