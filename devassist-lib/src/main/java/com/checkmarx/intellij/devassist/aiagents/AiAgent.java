package com.checkmarx.intellij.devassist.aiagents;

import com.checkmarx.intellij.devassist.configuration.mcp.AiAssistantMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.CopilotMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import com.checkmarx.intellij.devassist.remediation.AiAssistantChatIntegration;
import com.checkmarx.intellij.devassist.remediation.CopilotChatIntegration;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Serializes {@link #installMcp(String)}/{@link #uninstallMcp()} calls for this specific
     * agent. {@link #mcpTarget()} returns a fresh {@link McpAgentTarget} instance on every call
     * (so an instance-level lock would be useless - each caller would get its own, uncontended
     * lock), so this lock lives on the enum constant itself, which is a stable singleton.
     */
    private final Object mcpLock = new Object();

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
     * The MCP client target to install/uninstall the Checkmarx MCP server entry against for this
     * agent. Prefer {@link #installMcp(String)}/{@link #uninstallMcp()} over calling
     * {@code mcpTarget().install(...)}/{@code mcpTarget().uninstall()} directly, since those go
     * through this agent's lock and this one doesn't.
     */
    public McpAgentTarget mcpTarget() {
        return mcpTargetFactory.get();
    }

    /**
     * Installs the Checkmarx MCP server entry for this agent, serialized against any concurrent
     * {@link #uninstallMcp()} call for the same agent.
     *
     * @return true if the config file was modified, false if it was already up to date
     */
    public boolean installMcp(String credential) throws Exception {
        synchronized (mcpLock) {
            return mcpTarget().install(credential);
        }
    }

    /**
     * Removes the Checkmarx MCP server entry for this agent, serialized against any concurrent
     * {@link #installMcp(String)} call for the same agent.
     *
     * @return true if an entry was actually removed
     */
    public boolean uninstallMcp() throws Exception {
        synchronized (mcpLock) {
            return mcpTarget().uninstall();
        }
    }

    /**
     * Removes this agent's MCP entry on a background thread, logging (and optionally notifying
     * the EDT via {@code onFailure}) rather than propagating if it fails.
     * <p>
     * Used when the user switches away from this agent without logging out: the entry is no
     * longer tracked by anything once that happens, so it must be proactively cleaned up rather
     * than left to the next logout/plugin-uninstall.
     *
     * @param logger    the caller's logger (kept caller-side so log lines carry the calling
     *                  class's category, matching the rest of this codebase's logging convention)
     * @param context   short phrase describing why this is happening, appended to the log line
     *                  (e.g. {@code "after switching to AI Assistant"})
     * @param onFailure optional callback invoked on the EDT if the uninstall throws, so the UI
     *                  can surface that manual cleanup may be needed; may be {@code null}
     */
    public void uninstallMcpInBackground(Logger logger, String context, @Nullable Runnable onFailure) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                uninstallMcp();
            } catch (Exception ex) {
                logger.warn("Failed to remove Checkmarx MCP entry for " + agentName + " " + context, ex);
                if (onFailure != null) {
                    ApplicationManager.getApplication().invokeLater(onFailure);
                }
            }
        });
    }

    /**
     * Removes the Checkmarx MCP entry for every known agent, continuing even if one fails -
     * used wherever ALL agents' entries must be cleaned up (logout, full plugin uninstall) since
     * the user may have installed MCP for an agent other than the one currently selected.
     *
     * @param logger  the caller's logger
     * @param context short phrase describing why this is happening, appended to each log line
     *                (e.g. {@code "during plugin uninstallation"}, {@code "on logout"})
     */
    public static void uninstallFromAllAgents(Logger logger, String context) {
        for (AiAgent agent : values()) {
            try {
                boolean removed = agent.uninstallMcp();
                if (removed) {
                    logger.debug("Checkmarx MCP configuration removed for " + agent.agentName + " " + context);
                } else {
                    logger.debug("No Checkmarx MCP configuration found for " + agent.agentName + " " + context);
                }
            } catch (Exception ex) {
                logger.warn("Failed to remove Checkmarx MCP configuration for " + agent.agentName + " " + context, ex);
            }
        }
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
