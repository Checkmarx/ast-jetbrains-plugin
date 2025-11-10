package com.checkmarx.intellij.devassist.configuration.mcp;

import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Centralized asynchronous MCP installation logic and IDE startup auto-install activity.
 * <p>
 * Acts both as a service utility (static installSilentlyAsync) and as a post-startup activity when
 * registered in plugin.xml. On startup it auto-installs MCP configuration if:
 * <ul>
 *   <li>User is authenticated</li>
 *   <li>AI MCP server flag is enabled (TenantSetting.isAiMcpServerEnabled())</li>
 *   <li>A credential token/API key is available</li>
 * </ul>
 * If any condition fails the auto-install silently skips (debug logged).
 */
public final class McpInstallService implements StartupActivity.DumbAware {

    private static final Logger LOG = Logger.getInstance(McpInstallService.class);

    private McpInstallService() {
        // utility + startup activity
    }

    /**
     * Post-startup hook invoked by IntelliJ Platform. Performs conditional auto-install.
     */
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
        } catch (Exception e) {
            LOG.warn("Failed to check AI MCP server status; skipping MCP auto-install.", e);
            return;
        }

        if (!aiMcpEnabled) {
            LOG.debug("AI MCP server disabled; skipping MCP auto-install.");
            return;
        }

        String token = state.isApiKeyEnabled()
                ? sensitive.getApiKey()
                : sensitive.getRefreshToken();

        if (token == null || token.isBlank()) {
            LOG.debug("MCP auto-install skipped: no credential token available.");
            return;
        }

        // Execute asynchronously in background
        AppExecutorUtil.getAppExecutorService().execute(() -> installSilentlyAsync(token));
    }

    /**
     * Installs MCP configuration asynchronously, without user notifications.
     * @param credential token / API key for Authorization header
     * @return future resolving to Boolean (true = changed, false = unchanged, null = error)
     */
    public static CompletableFuture<Boolean> installSilentlyAsync(String credential) {
        if (credential == null || credential.isBlank()) {
            LOG.debug("MCP install skipped: empty credential.");
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return McpSettingsInjector.installForCopilot(credential); // true if modified
            } catch (Exception ex) {
                LOG.warn("MCP install failed", ex);
                return null; // null signals failure
            }
        }, AppExecutorUtil.getAppExecutorService());
    }
}
