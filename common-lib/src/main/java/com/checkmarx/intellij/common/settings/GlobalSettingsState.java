package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * State object for not sensitive global settings for the plugin.
 */
@Getter
@Setter
@EqualsAndHashCode
@State(name = Constants.GLOBAL_SETTINGS_STATE_NAME, storages = @Storage(Constants.GLOBAL_SETTINGS_STATE_FILE))
public class GlobalSettingsState implements PersistentStateComponent<GlobalSettingsState> {

    private Logger logger = Utils.getLogger(GlobalSettingsState.class);

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

    @NotNull
    @Transient
    private transient Set<Filterable> filters = FilterProviderRegistry.getInstance().getDefaultFilters();

    /**
     * Persisted representation of the selected filters as filter value strings.
     * This survives IDE restarts while keeping the runtime {@code filters} set free
     * from non-serializable objects that would cause XMLB ClassCastExceptions.
     * Null means "use defaults" (first launch / legacy state).
     */
    @Nullable
    private Set<String> filterValues = null;

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

    // Getters for license value
    // Setters for license value
    @Attribute("isDevAssistLicenseEnabled")
    private boolean isDevAssistLicenseEnabled = false;

    @Attribute("isOneAssistLicenseEnabled")
    private boolean isOneAssistLicenseEnabled = false;

    // Getters and setters for ASCA realtime
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
        initializeDefaultFiltersIfMissing();

        if (shouldRefreshFiltersFromPersisted()) {
            filters = restoreFiltersFromPersisted();
        }

        // Sync runtime filter objects → persisted string values before XMLB serializes this bean
        if (!isInvalidFilterCollection(filters)) {
            // If provider is not ready yet, keep unresolved persisted values instead of dropping custom states.
            if (!(hasUnresolvedPersistedFilters() && !FilterProviderRegistry.getInstance().hasProvider())) {
                filterValues = currentFilterValues();
            }
        }
        return this;
    }

    @Transient
    public @NotNull Set<Filterable> getFilters() {
        // Initialize defaults if no filters have been set
        initializeDefaultFiltersIfMissing();

        // If runtime filters are invalid or need refresh, restore from persisted values
        if (isInvalidFilterCollection(filters) || shouldRefreshFiltersFromPersisted()) {
            filters = restoreFiltersFromPersisted();
        }
        
        return filters;
    }

    @Transient
    public void setFilters(@Nullable Set<Filterable> newFilters) {
        if (newFilters == null || isInvalidFilterCollection(newFilters)) {
            this.filters = FilterProviderRegistry.getInstance().getDefaultFilters();
        } else {
            this.filters = new LinkedHashSet<>(newFilters);
        }
        // Always keep persisted string set in sync with runtime filters
        this.filterValues = toFilterValues(this.filters);
        logger.info("Filters updated by user. Persisted filter count: " + (filterValues != null ? filterValues.size() : 0));
    }

    @Override
    public void loadState(@NotNull GlobalSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);

        /*
         * Restore the runtime filter collection from the persisted string values.
         * filterValues is already copied by copyBean above.
         * If null (first launch / legacy state), fall back to defaults.
         */
        if (filterValues != null && !filterValues.isEmpty()) {
            logger.info("Loading persisted filters from storage. Filter count: " + filterValues.size());
            filters = restoreFiltersFromPersisted();
        } else {
            logger.info("No persisted filters found. Initializing with defaults.");
            initializeDefaultFiltersIfMissing();
        }
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
     *
     * Sets user preferences for realtime scanners, preserving individual choices across MCP enable/disable cycles.
     *
     * @param ascaRealtime            ASCA scanner preference
     * @param ossRealtime             OSS scanner preference
     * @param secretDetectionRealtime Secret Detection scanner preference
     * @param containersRealtime      Containers scanner preference
     * @param iacRealtime             Infrastructure as Code scanner preference
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

    // Getters for user preferences (for debugging and verification)
    public boolean getUserPreferencesSet() {
        return userPreferencesSet;
    }

    public boolean getUserPrefAscaRealtime() {
        return userPrefAscaRealtime;
    }

    public boolean getUserPrefOssRealtime() {
        return userPrefOssRealtime;
    }

    public boolean getUserPrefSecretDetectionRealtime() {
        return userPrefSecretDetectionRealtime;
    }

    public boolean getUserPrefContainersRealtime() {
        return userPrefContainersRealtime;
    }

    public boolean getUserPrefIacRealtime() {
        return userPrefIacRealtime;
    }

    /**
     * Rebuilds the runtime {@code filters} set from the persisted string values.
     * Uses {@link FilterProviderRegistry#resolveFilterByValue(String)} for each value.
     * Falls back to defaults if persisted values are null/empty.
     */
    private Set<Filterable> restoreFiltersFromPersisted() {
        if (filterValues == null) {
            return FilterProviderRegistry.getInstance().getDefaultFilters();
        }
        return filterValues.stream()
                .map(value -> FilterProviderRegistry.getInstance().resolveFilterByValue(value))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> currentFilterValues() {
        return toFilterValues(filters);
    }

    private boolean hasUnresolvedPersistedFilters() {
        if (filterValues == null || filterValues.isEmpty() || isInvalidFilterCollection(filters)) {
            return false;
        }
        return !currentFilterValues().containsAll(filterValues);
    }

    private boolean shouldRefreshFiltersFromPersisted() {
        return FilterProviderRegistry.getInstance().hasProvider() && hasUnresolvedPersistedFilters();
    }

    private void initializeDefaultFiltersIfMissing() {
        if (filterValues != null) {
            return;
        }

        // No filters persisted yet - initialize with defaults from provider
        filters = FilterProviderRegistry.getInstance().getDefaultFilters();
        filterValues = toFilterValues(filters);
        
        logger.info("Initialized default filters on first launch. Filter count: " + filterValues.size());
    }

    private Set<String> toFilterValues(Set<Filterable> sourceFilters) {
        return sourceFilters.stream()
                .map(Filterable::getFilterValue)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Checks if the filter collection is invalid (null/empty or contains non-filter objects).
     * This is needed because XML deserialization can sometimes populate the Set with String objects
     * instead of Filterable objects, causing ClassCastException.
     */
    private boolean isInvalidFilterCollection(@Nullable Set<Filterable> filterSet) {
        if (filterSet == null) {
            return true;
        }
        try {
            // Check if all elements are proper Filterable instances and not null
            for (Object element : filterSet) {
                if (!(element instanceof Filterable)) {
                    return true;
                }
            }
            return filterSet.stream().anyMatch(Objects::isNull);
        } catch (ClassCastException e) {
            logger.debug("Failed to validate filter collection.", e);
            return true;
        }
    }
}
   