package com.checkmarx.intellij.devassist.configuration;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.PluginVersionProvider;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Detects plugin install/update on IDE startup by comparing the current plugin version
 * against the last version persisted in {@link GlobalSettingsState}, and reports the
 * result via {@link TelemetryService}.
 * <p>
 * Runs once per project open. Only the first project opened after a version change triggers
 * an event, since the persisted version is updated immediately after detection.
 */
public class PluginInstallTrackingStartupActivity implements StartupActivity.DumbAware {

    private static final Logger LOGGER = Utils.getLogger(PluginInstallTrackingStartupActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        String currentVersion = PluginVersionProvider.getPluginVersion();
        if (currentVersion == null || currentVersion.isEmpty()) {
            LOGGER.debug("PluginInstallTrackingStartupActivity: Current plugin version unavailable, skipping install/update detection");
            return;
        }

        GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
        if (settingsState == null) {
            return;
        }

        String lastKnownVersion = settingsState.getLastKnownPluginVersion();
        if (currentVersion.equals(lastKnownVersion)) {
            return;
        }

        if (lastKnownVersion == null || lastKnownVersion.isEmpty()) {
            LOGGER.debug("PluginInstallTrackingStartupActivity: No previously-known version found, treating as fresh install (version " + currentVersion + ")");
            TelemetryService.logPluginInstalled();
        } else {
            LOGGER.debug("PluginInstallTrackingStartupActivity: Plugin version changed from " + lastKnownVersion + " to " + currentVersion + ", treating as update");
            TelemetryService.logPluginUpdated();
        }

        settingsState.setLastKnownPluginVersion(currentVersion);
    }
}
