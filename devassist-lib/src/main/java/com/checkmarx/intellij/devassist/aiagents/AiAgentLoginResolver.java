package com.checkmarx.intellij.devassist.aiagents;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves which {@link AiAgent} should be active right after a successful authentication, and
 * whether a notice about that resolution should be shown in the welcome dialog.
 * <p>
 * Called on every successful login (not just first-ever setup), so a previously-selected agent
 * that later gets uninstalled is caught and replaced on the very next login.
 */
public final class AiAgentLoginResolver {

    private static final Logger LOGGER = Utils.getLogger(AiAgentLoginResolver.class);

    private AiAgentLoginResolver() {
        // Utility class
    }

    /**
     * @param project the project context; may be {@code null} for a global (non-project-scoped) check
     * @param state   the settings state to read the currently configured agent from, and to
     *                update in place if a different agent is auto-selected
     */
    public static AiAgentResolution resolve(@Nullable Project project, GlobalSettingsState state) {
        List<AiAgent> installedAgents = AiAgent.installedAgents(project);

        if (installedAgents.isEmpty()) {
            // Nothing is installed at all: still park a sensible default in settings for once the
            // user installs something, based purely on IDE-version eligibility.
            AiAgent chosenDefault = AiAgent.computeVersionBasedDefault();
            state.setAiAgent(chosenDefault.name());
            LOGGER.warn("AI-Agents: No AI agents are installed; defaulting to " + chosenDefault.name());
            return new AiAgentResolution(chosenDefault, Bundle.message(Resource.AI_AGENT_NO_AGENT_INSTALLED,
                    DevAssistUtils.getCustomPluginDisplayName()));
        }

        AiAgent defaultAgent = AiAgent.tryResolveConfigured(state.getAiAgent()).orElse(AiAgent.computeVersionBasedDefault());

        if (installedAgents.contains(defaultAgent)) {
            LOGGER.info("AI-Agents: Configured agent " + defaultAgent.name() + " is installed; using it");
            return new AiAgentResolution(defaultAgent, null);
        }

        // The default agent isn't installed, but something else supported is: switch to the
        // highest-preference installed agent and name the missing default in the notice.
        AiAgent fallback = AiAgent.resolveBestInstalledAgent(installedAgents)
                .orElseThrow(() -> new IllegalStateException(
                        "installedAgents() was non-empty but resolveBestInstalledAgent() found nothing"));
        state.setAiAgent(fallback.name());
        LOGGER.warn("AI-Agents: Configured agent " + defaultAgent + " is not installed; auto-switching to " + fallback.name());
        return new AiAgentResolution(fallback, Bundle.message(Resource.AI_AGENT_AUTO_SWITCHED, defaultAgent.getAgentName(),
                DevAssistUtils.getCustomPluginDisplayName(), fallback.getAgentName()));
    }
}
