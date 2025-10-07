package com.checkmarx.intellij.service;

import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.notification.NotificationType;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.checkmarx.intellij.commands.TenantSetting;
import org.jetbrains.annotations.NotNull;

/**
 * Auto-installs MCP entry for GitHub Copilot after IDE startup
 * if the user is authenticated and AI MCP server is enabled.
 */
public class McpAutoInstallService implements StartupActivity.DumbAware {
    private static final Logger LOG = Logger.getInstance(McpAutoInstallService.class);

    @Override
    public void runActivity(@NotNull Project project) {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitive = GlobalSettingsSensitiveState.getInstance();

        if (!state.isAuthenticated()) {
            LOG.debug("MCP auto-install skipped: user not authenticated.");
            return;
        }

        boolean aiMcpEnabled;
        try {
            aiMcpEnabled = TenantSetting.isAiMcpServerEnabled();
        } catch (Exception e) { // catches IOException, CxException, InterruptedException
            LOG.warn("Failed to check AI MCP server status; skipping MCP auto-install.", e);
            return;
        }

        if (!aiMcpEnabled) {
            LOG.debug("AI MCP Server is disabled; skipping MCP auto-install at startup.");
            return;
        }

        String token = state.isApiKeyEnabled() ? sensitive.getApiKey() : sensitive.getRefreshToken();
        if (token == null || token.isBlank()) {
            LOG.debug("MCP auto-install skipped: no token available.");
            return;
        }

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                boolean changed = McpSettingsInjector.installForCopilot(token);
                String message = changed ? "MCP configuration saved successfully." : "MCP configuration already up to date.";
                Utils.showNotification("Checkmarx MCP", message, NotificationType.INFORMATION, project);
                LOG.debug("MCP auto-install at startup: " + message);
            } catch (Exception e) {
                LOG.warn("MCP auto-install on startup failed.", e);
                Utils.showNotification("Checkmarx MCP", "An unexpected error occurred during MCP setup.", NotificationType.ERROR, project);
            }
        });
    }
}
