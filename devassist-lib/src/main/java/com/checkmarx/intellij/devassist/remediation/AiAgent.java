package com.checkmarx.intellij.devassist.remediation;

import com.checkmarx.intellij.devassist.configuration.mcp.AiAssistantMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.CopilotMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * The AI chat agent that receives generated fix/explanation prompts from CxOne Assist, and the
 * MCP client whose config the Checkmarx MCP server entry gets installed into for that agent.
 * <p>
 * This enum doubles as the extension registry: each constant wires a {@link ChatIntegration}
 * and an {@link McpAgentTarget} implementation. To add support for a new agent:
 * <ol>
 *   <li>Implement {@link ChatIntegration} (how to open its chat and deliver a prompt)</li>
 *   <li>Implement {@link McpAgentTarget} (how to install/uninstall the MCP entry, and where its
 *       config file / settings page lives)</li>
 *   <li>Add one constant here wiring the two</li>
 * </ol>
 * No changes are needed in {@link RemediationManager}, {@code McpInstallService}, or the
 * settings UI - they all resolve behavior through {@link #chatIntegration()} / {@link #mcpTarget()}
 * and build their agent picker from {@link #values()}.
 */
public enum AiAgent {

    COPILOT("Copilot", CopilotChatIntegration::new, CopilotMcpTarget::new),
    AI_ASSISTANT("AI Assistant", AiAssistantChatIntegration::new, AiAssistantMcpTarget::new);

    @Getter
    private final String agentName;
    private final Supplier<ChatIntegration> chatIntegrationFactory;
    private final Supplier<McpAgentTarget> mcpTargetFactory;

    AiAgent(String agentName, Supplier<ChatIntegration> chatIntegrationFactory, Supplier<McpAgentTarget> mcpTargetFactory) {
        this.agentName = agentName;
        this.chatIntegrationFactory = chatIntegrationFactory;
        this.mcpTargetFactory = mcpTargetFactory;
    }

    /**
     * The chat integration to send fix/explanation prompts to for this agent.
     */
    public ChatIntegration chatIntegration() {
        return chatIntegrationFactory.get();
    }

    /**
     * The MCP client target to install/uninstall the Checkmarx MCP server entry against for this agent.
     */
    public McpAgentTarget mcpTarget() {
        return mcpTargetFactory.get();
    }

    /**
     * Resolves a persisted {@link com.checkmarx.intellij.common.settings.GlobalSettingsState#getAiAgent()}
     * value to an {@link AiAgent}, defaulting to {@link #COPILOT} for null/unknown/legacy values.
     */
    public static AiAgent fromSettingsValue(String value) {
        if (value == null) {
            return COPILOT;
        }
        try {
            return AiAgent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return COPILOT;
        }
    }

    /**
     * Resolves an agent name to an {@link AiAgent}, defaulting to {@link #COPILOT} for null/unknown values.
     */
    public static AiAgent fromAgentName(String agentName) {
        for (AiAgent agent : AiAgent.values()) {
            if (agent.getAgentName().equalsIgnoreCase(agentName)) {
                return agent;
            }
        }
        return COPILOT; // Default to COPILOT if no match is found
    }

}
