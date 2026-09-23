package com.checkmarx.intellij.devassist.aiagents;

import com.checkmarx.intellij.devassist.configuration.mcp.AiAssistantMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.CopilotMcpTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import com.checkmarx.intellij.devassist.aiagents.aiassistant.AiAssistantChatIntegration;
import com.checkmarx.intellij.devassist.aiagents.aiassistant.AiAssistantIntegration;
import com.checkmarx.intellij.devassist.aiagents.copilot.CopilotChatIntegration;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This enum doubles as the extension registry: each constant wires a {@link ChatIntegration}
 * and an {@link McpAgentTarget} implementation. To add support for a new agent:
 * <ol>
 *   <li>Implement {@link ChatIntegration} (how to open its chat and deliver a prompt)</li>
 *   <li>Implement {@link McpAgentTarget} (how to install/uninstall the MCP entry, and where its
 *       config file / settings page lives)</li>
 *   <li>Add one constant here wiring the two</li>
 * </ol>
 */
public enum AiAgent {

    COPILOT("Copilot", CopilotChatIntegration::new, CopilotMcpTarget::new, "GitHub Copilot", () -> true),
    JETBRAINS_AI_ASSISTANT("JetBrains AI Assistant", AiAssistantChatIntegration::new, AiAssistantMcpTarget::new,
            "JetBrains AI Assistant", AiAssistantIntegration::supportsAcp);

    /**
     * Static ranking used to pick a default agent - see {@link #preferenceOrder()}. Declared
     * highest-preference first; any new {@link AiAgent} constant just needs adding here in its
     * intended rank.
     */
    private static final List<AiAgent> PREFERENCE_ORDER = List.of(JETBRAINS_AI_ASSISTANT, COPILOT);

    @Getter
    private final String agentName;
    private final Supplier<ChatIntegration> chatIntegrationFactory;
    private final Supplier<McpAgentTarget> mcpTargetFactory;

    /**
     * Whether this agent is currently eligible to occupy its declared slot in
     * {@link #PREFERENCE_ORDER} (e.g. JetBrains AI Chat only while the IDE version supports ACP -
     */
    private final Supplier<Boolean> defaultEligible;

    /**
     * Query used to pre-search the IDE's Marketplace tab for this agent's plugin
     * - the plugin's actual Marketplace listing name
     */
    private final String marketplaceSearchQuery;

    /**
     * Serializes {@link #installMcp(String)}/{@link #uninstallMcp()} calls for this specific agent.
     */
    private final Object mcpLock = new Object();

    AiAgent(String agentName, Supplier<ChatIntegration> chatIntegrationFactory, Supplier<McpAgentTarget> mcpTargetFactory,
            String marketplaceSearchQuery, Supplier<Boolean> defaultEligible) {
        this.agentName = agentName;
        this.chatIntegrationFactory = chatIntegrationFactory;
        this.mcpTargetFactory = mcpTargetFactory;
        this.marketplaceSearchQuery = marketplaceSearchQuery;
        this.defaultEligible = defaultEligible;
    }

    /**
     * The chat integration to send fix/explanation prompts to for this agent.
     */
    public ChatIntegration chatIntegration() {
        return chatIntegrationFactory.get();
    }

    /**
     * Whether this agent's plugin is installed (and enabled) in the current IDE
     * @param project the project context; may be {@code null} for a global (non-project-scoped) check
     */
    public boolean isInstalled(@Nullable Project project) {
        return chatIntegration().isAvailable(project);
    }

    /**
     * Opens the IDE's own Plugins settings page directly on the Marketplace tab.
     *
     * @param project the project context; may be {@code null} to use the default project
     */
    public void openMarketplacePage(@Nullable Project project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable.class,
                configurable -> configurable.openMarketplaceTab(marketplaceSearchQuery));
    }

    /**
     * The MCP client target to install/uninstall the Checkmarx MCP server entry against for this agent.
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
     * used wherever ALL agents' entries must be cleaned up (logout, full plugin uninstall).
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
        if (value == null || value.isBlank()) {
            return COPILOT;
        }
        try {
            return AiAgent.valueOf(value);
        } catch (IllegalArgumentException e) {
            return COPILOT;
        }
    }

    /**
     * Resolves a persisted settings value to an {@link AiAgent} without defaulting - returns
     * {@link Optional#empty()} for a blank/null/unrecognized value instead of silently.
     */
    public static Optional<AiAgent> tryResolveConfigured(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AiAgent.valueOf(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * All agents whose plugin is currently installed and enabled, in declaration order.
     */
    public static List<AiAgent> installedAgents(@Nullable Project project) {
        return Arrays.stream(values())
                .filter(agent -> agent.isInstalled(project))
                .collect(Collectors.toList());
    }

    /**
     * {@link #PREFERENCE_ORDER}, with any currently-ineligible agent (see
     * {@link #defaultEligible}) demoted behind the next one - e.g. JetBrains AI Chat drops behind
     * Copilot when the current IDE version doesn't support ACP - rather than removed, so the
     * ranking always contains every known agent.
     */
    public static List<AiAgent> preferenceOrder() {
        return PREFERENCE_ORDER.stream()
                .sorted(Comparator.comparing(agent -> Boolean.TRUE.equals(agent.defaultEligible.get()) ? 0 : 1))
                .collect(Collectors.toList());
    }

    /**
     * The agent to default to when nothing is configured yet (or the configured agent is no
     * longer installed and nothing else is either) - the top of {@link #preferenceOrder()},
     * regardless of whether it's actually installed.
     */
    public static AiAgent computeVersionBasedDefault() {
        return preferenceOrder().get(0);
    }

    /**
     * Picks the best installed agent to use as a default: the highest-{@link #preferenceOrder()}
     * agent that's also actually installed, or empty if none are installed.
     * <p>
     * Adding a new {@link AiAgent} constant needs no change here - it participates automatically
     * through {@link #isInstalled(Project)} and its slot in {@link #PREFERENCE_ORDER}.
     */
    public static Optional<AiAgent> resolveBestInstalledAgent(List<AiAgent> installedAgents) {
        return preferenceOrder().stream()
                .filter(installedAgents::contains)
                .findFirst();
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
