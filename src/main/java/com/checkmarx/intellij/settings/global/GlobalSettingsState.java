package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.service.StateService;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * State object for not sensitive global settings for the plugin.
 */
@Getter
@Setter
@EqualsAndHashCode
@State(name = Constants.GLOBAL_SETTINGS_STATE_NAME, storages = @Storage(Constants.GLOBAL_SETTINGS_STATE_FILE))
public class GlobalSettingsState implements PersistentStateComponent<GlobalSettingsState> {

    private StateService stateService = StateService.getInstance();

    public static GlobalSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsState.class);
    }

    private String validationMessage = "";

    @NotNull
    private String additionalParameters = "";

    private boolean asca = false;
    private boolean isApiKeyEnabled = false;

    @Attribute("authenticated")
    private boolean authenticated = false;

    @Attribute("lastValidationSuccess")
    private boolean lastValidationSuccess = true;

    private String refreshTokenExpiry;

    private String validationExpiry;

    public @NotNull Set<Filterable> getFilters() {
        if (filters.isEmpty() || filters.stream().allMatch(Objects::isNull)) {
            filters = stateService.getDefaultFilters();
        }
        return filters;
    }

    @NotNull
    private Set<Filterable> filters = stateService.getDefaultFilters();

    private String baseUrl = "";
    private String tenant = "";

    @Attribute("validationInProgress")
    private boolean validationInProgress = false;

    // --- CxOne Assist realtime feature flags and options ---
    private boolean ossRealtime = false;
    private boolean secretDetectionRealtime = false;
    private boolean containersRealtime = false;
    private boolean iacRealtime = false;
    private String containersTool = "docker";
    @Attribute("mcpEnabled")
    private boolean mcpEnabled = false;
    @Attribute("mcpStatusChecked")
    private boolean mcpStatusChecked = false;
    @Attribute("welcomeShown")
    private boolean welcomeShown = false;

    @Attribute("isDevAssistLicenseEnabled")
    private boolean isDevAssistLicenseEnabled = false;

    @Attribute("isOneAssistLicenseEnabled")
    private boolean isOneAssistLicenseEnabled = false;

    // --- Realtime Scanner Settings ---
    private boolean ascaRealtime = false;

    // --- User preferences for realtime scanners (preserved across MCP enable/disable cycles) ---
    /**
     * These fields store the user's individual scanner preferences and are preserved even when MCP is disabled at the tenant level.
     */

    @Attribute("userPreferencesSet")
    private boolean userPreferencesSet = false;

    @Attribute("userPrefAscaRealtime")
    private boolean userPrefAscaRealtime = false;

    @Attribute("userPrefOssRealtime")
    private boolean userPrefOssRealtime = false;

    @Attribute("userPrefSecretDetectionRealtime")
    private boolean userPrefSecretDetectionRealtime = false;

    @Attribute("userPrefContainersRealtime")
    private boolean userPrefContainersRealtime = false;

    @Attribute("userPrefIacRealtime")
    private boolean userPrefIacRealtime = false;

    @Override
    public @Nullable GlobalSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GlobalSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    /**
     * Applies the given state to this instance, copying all fields including user preferences.
     * This ensures that user preferences are preserved during state transitions.
     */
    public void apply(@NotNull GlobalSettingsState state) {
        loadState(state);
    }

    // --- User Preference Methods ---

    /**
    /**
     * Sets user preferences for realtime scanners, preserving individual choices across MCP enable/disable cycles.
     *
     * @param ascaRealtime ASCA scanner preference
     * @param ossRealtime OSS scanner preference
     * @param secretDetectionRealtime Secret Detection scanner preference
     * @param containersRealtime Containers scanner preference
     * @param iacRealtime Infrastructure as Code scanner preference
     */
    public void setUserPreferences(boolean ascaRealtime, boolean ossRealtime, boolean secretDetectionRealtime,
                                   boolean containersRealtime, boolean iacRealtime) {
        this.userPrefAscaRealtime = ascaRealtime;
        this.userPrefOssRealtime = ossRealtime;
        this.userPrefSecretDetectionRealtime = secretDetectionRealtime;
        this.userPrefContainersRealtime = containersRealtime;
        this.userPrefIacRealtime = iacRealtime;
        this.userPreferencesSet = true;
    }


    /**
     * Applies stored user preferences to the active realtime scanner settings.
     * This is called when MCP is enabled to restore the user's individual scanner choices
     * instead of defaulting to "all enabled".
     *
     * @return true if any settings were changed, false if preferences were already applied or not set
     */
    public boolean applyUserPreferencesToRealtimeSettings() {
        if (!userPreferencesSet) {
            return false; // No user preferences stored
        }

        boolean changed = false;
        if (ascaRealtime != userPrefAscaRealtime) {
            ascaRealtime = userPrefAscaRealtime;
            changed = true;
        }
        if (ossRealtime != userPrefOssRealtime) {
            ossRealtime = userPrefOssRealtime;
            changed = true;
        }
        if (secretDetectionRealtime != userPrefSecretDetectionRealtime) {
            secretDetectionRealtime = userPrefSecretDetectionRealtime;
            changed = true;
        }
        if (containersRealtime != userPrefContainersRealtime) {
            containersRealtime = userPrefContainersRealtime;
            changed = true;
        }
        if (iacRealtime != userPrefIacRealtime) {
            iacRealtime = userPrefIacRealtime;
            changed = true;
        }

        return changed;
    }

    /**
     * Saves the current realtime scanner settings as user preferences.
     * This is typically called before disabling scanners when MCP becomes unavailable,
     * ensuring the user's choices can be restored later.
     */
    public void saveCurrentSettingsAsUserPreferences() {
        setUserPreferences(ascaRealtime, ossRealtime, secretDetectionRealtime, containersRealtime, iacRealtime);
    }

    /**
     * Checks if the user has set any custom preferences that differ from the default "all enabled" state.
     * This helps distinguish between new users (who should get defaults) and existing users
     * (whose custom choices should be preserved).
     *
     * @return true if user has any scanners disabled in their preferences
     */
    public boolean hasCustomUserPreferences() {
        return userPreferencesSet && (
                !userPrefAscaRealtime ||
                        !userPrefOssRealtime ||
                        !userPrefSecretDetectionRealtime ||
                        !userPrefContainersRealtime ||
                        !userPrefIacRealtime
        );
    }

    // Getters and setters for ASCA realtime
    public boolean isAscaRealtime() { return ascaRealtime; }
    public void setAscaRealtime(boolean ascaRealtime) { this.ascaRealtime = ascaRealtime; }



    // Getters for user preferences (for debugging and verification)
    public boolean getUserPreferencesSet() { return userPreferencesSet; }
    public boolean getUserPrefAscaRealtime() { return userPrefAscaRealtime; }
    public boolean getUserPrefOssRealtime() { return userPrefOssRealtime; }
    public boolean getUserPrefSecretDetectionRealtime() { return userPrefSecretDetectionRealtime; }
    public boolean getUserPrefContainersRealtime() { return userPrefContainersRealtime; }
    public boolean getUserPrefIacRealtime() { return userPrefIacRealtime; }

    // Getters for license value
    public boolean isDevAssistLicenseEnabled() { return isDevAssistLicenseEnabled; }
    public boolean isOneAssistLicenseEnabled() { return isOneAssistLicenseEnabled; }

    // Setters for license value 
    public void setDevAssistLicenseEnabled(boolean isDevAssistLicenseEnabled) { this.isDevAssistLicenseEnabled = isDevAssistLicenseEnabled; }
    public void setOneAssistLicenseEnabled(boolean isOneAssistLicenseEnabled) { this.isOneAssistLicenseEnabled = isOneAssistLicenseEnabled; }
}
