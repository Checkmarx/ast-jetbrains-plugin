package com.checkmarx.intellij.startup;

import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Startup activity that syncs license flags from the server on IDE restart.
 * This ensures that the UI panels (CxFindingsWindow, CxToolWindowPanel, CxIgnoredFindings)
 * display the correct content based on the latest license status from the server.
 *
 * The activity:
 * 1. Checks if the user is authenticated
 * 2. Fetches tenant settings from the API to get license flags
 * 3. Updates GlobalSettingsState with the fetched flags
 * 4. Publishes a SETTINGS_APPLIED event via MessageBus to trigger UI redraw
 *
 * Implements DumbAware to allow execution in background thread during indexing.
 */
public class LicenseFlagSyncStartupActivity implements StartupActivity.DumbAware {
    private static final Logger LOGGER = Logger.getInstance(LicenseFlagSyncStartupActivity.class);

    @Override
    public void runActivity(@NotNull Project project) {
        LOGGER.debug("LicenseSyncStartupActivity: Starting license flag sync for project: " + project.getName());

        GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();

        // Only sync if user is authenticated
        if (!settingsState.isAuthenticated()) {
            LOGGER.debug("LicenseSyncStartupActivity: User not authenticated, skipping license flag sync");
            return;
        }

        // Fetch license flags from API in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                LOGGER.debug("LicenseSyncStartupActivity: Fetching tenant settings from API");
                
                Map<String, String> tenantSettings = TenantSetting.getTenantSettingsMap(settingsState, sensitiveState);
                
                boolean devAssistEnabled = Boolean.parseBoolean(
                        tenantSettings.getOrDefault(TenantSetting.KEY_DEV_ASSIST, "false"));
                boolean oneAssistEnabled = Boolean.parseBoolean(
                        tenantSettings.getOrDefault(TenantSetting.KEY_ONE_ASSIST, "false"));

                LOGGER.debug("LicenseSyncStartupActivity: Fetched license flags - devAssist=" + devAssistEnabled + ", oneAssist=" + oneAssistEnabled);

                // Update GlobalSettingsState with fetched flags
                boolean flagsChanged = false;
                if (settingsState.isDevAssistLicenseEnabled() != devAssistEnabled) {
                    settingsState.setDevAssistLicenseEnabled(devAssistEnabled);
                    flagsChanged = true;
                    LOGGER.debug("LicenseSyncStartupActivity: Updated devAssist flag to " + devAssistEnabled);
                }
                if (settingsState.isOneAssistLicenseEnabled() != oneAssistEnabled) {
                    settingsState.setOneAssistLicenseEnabled(oneAssistEnabled);
                    flagsChanged = true;
                    LOGGER.debug("LicenseSyncStartupActivity: Updated oneAssist flag to " + oneAssistEnabled);
                }

                // If flags changed, publish settings change event to trigger UI redraw
                // Must be done on EDT since UI panels will redraw in response
                if (flagsChanged) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ApplicationManager.getApplication().getMessageBus()
                                .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                                .settingsApplied();
                        LOGGER.debug("LicenseSyncStartupActivity: SETTINGS_APPLIED event published, UI panels will redraw");
                    });
                } else {
                    LOGGER.debug("LicenseSyncStartupActivity: License flags unchanged, no UI update needed");
                }

            } catch (Exception e) {
                LOGGER.warn("LicenseSyncStartupActivity: Failed to fetch license flags from API", e);
                // Don't change existing flags on error - keep cached values
            }
        });
    }
}

