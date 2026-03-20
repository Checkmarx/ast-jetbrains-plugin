package com.checkmarx.intellij.common.settings;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
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

    /**
     * Runtime (transient) filter set. Null means "not yet resolved"; resolved lazily by
     * {@link #getFilters()} from {@link #filterValues} or from provider defaults.
     */
    @Transient
    private transient Set<Filterable> filters = null;

    /**
     * True when {@link #filters} was resolved while a {@link FilterProvider} was registered.
     * False means resolution was partial (severities only) and must be retried once the
     * provider registers.  This flag is never persisted.
     */
    @Transient
    private transient volatile boolean resolvedWithProvider = false;

    /**
     * Persisted representation of the selected filters as filter-value strings.
     * Survives IDE restarts. {@code null} means first launch (use full defaults).
     * An empty set means the user removed every filter — also falls back to defaults.
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
        // Only sync filterValues from the runtime filter set when it has been fully resolved
        // with a registered provider. If filters is still null (provider not registered yet),
        // the filterValues loaded from storage are correct as-is – do NOT overwrite them
        // with a partial (severity-only) set.
        if (resolvedWithProvider && filters != null && !isInvalidFilterCollection(filters)) {
            filterValues = toFilterValues(filters);
        }
        return this;
    }

    /**
     * Returns the active filter set, lazily resolved from {@link #filterValues}.
     *
     * <p>If the last resolution ran before a {@link FilterProvider} was registered
     * (e.g. very early during IDE startup), the set is re-resolved automatically
     * once a provider becomes available, using the original persisted strings.
     */
    @Transient
    public @NotNull Set<Filterable> getFilters() {
        boolean providerNowAvailable = FilterProviderRegistry.getInstance().hasProvider();
        if (filters == null || isInvalidFilterCollection(filters)
                || (providerNowAvailable && !resolvedWithProvider)) {
            // Re-resolve: either not yet resolved, or provider just became available
            filters = resolveFiltersFromPersistedValues();
        }
        return filters;
    }

    /**
     * Replaces the active filter set with {@code newFilters}.
     *
     * <p>After accepting the caller's selection, {@link #ensureMinimumFiltersPresent()}
     * guarantees that at least one default-severity item <em>and</em> at least one
     * default-custom-state item are always kept (requirement 6).
     */
    @Transient
    public void setFilters(@Nullable Set<Filterable> newFilters) {
        if (newFilters == null || isInvalidFilterCollection(newFilters)) {
            this.filters = FilterProviderRegistry.getInstance().getDefaultFilters();
        } else {
            this.filters = new LinkedHashSet<>(newFilters);
            ensureMinimumFiltersPresent();
        }
        // Always keep the persisted string set in sync with the runtime filters
        this.filterValues = toFilterValues(this.filters);
        // setFilters is only called from UI actions (after provider registers)
        this.resolvedWithProvider = FilterProviderRegistry.getInstance().hasProvider();
        logger.debug("Filters updated by user.");
    }

    @Override
    public void loadState(@NotNull GlobalSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
        // Reset runtime state so filters are lazily re-resolved from the just-restored
        // filterValues on the next call to getFilters().
        filters = null;
        resolvedWithProvider = false;
        logger.debug("State loaded from storage.");
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
     * Resolves the runtime {@link #filters} set from the persisted {@link #filterValues} strings.
     *
     * <ul>
     *   <li>If {@code filterValues} is {@code null} or empty (first launch / fully cleared state),
     *       the full provider-default set is used and {@code filterValues} is populated.</li>
     *   <li>Otherwise each persisted string is resolved back to a {@link Filterable} via
     *       {@link FilterProviderRegistry#resolveFilterByValue(String)}.</li>
     *   <li>If the resolved set has no {@link SeverityFilter} → all default severities are added
     *       (requirement 5).</li>
     *   <li>If the resolved set has no non-{@link SeverityFilter} entry (custom state) <em>and
     *       a provider is registered</em> → all default custom-state filters are added (req 4).
     *       When no provider is registered the persisted strings are preserved so the next
     *       call (after the provider registers) can complete the resolution.</li>
     * </ul>
     *
     * <p>{@code filterValues} is updated <em>only</em> when a provider is registered so that
     * custom-state strings are never stripped from storage during early startup.
     */
    private Set<Filterable> resolveFiltersFromPersistedValues() {
        boolean providerAvailable = FilterProviderRegistry.getInstance().hasProvider();

        if (filterValues == null || filterValues.isEmpty()) {
            // First launch or fully-cleared storage → start with full defaults
            Set<Filterable> defaults = FilterProviderRegistry.getInstance().getDefaultFilters();
            filterValues = toFilterValues(defaults);
            resolvedWithProvider = providerAvailable;
            logger.info("No persisted filter values found. Initialising with defaults. Count: "
                    + filterValues.size());
            return new LinkedHashSet<>(defaults);
        }

        // Resolve each persisted string to a live Filterable instance
        Set<Filterable> resolved = filterValues.stream()
                .map(v -> FilterProviderRegistry.getInstance().resolveFilterByValue(v))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Filterable> defaults = FilterProviderRegistry.getInstance().getDefaultFilters();

        // Requirement 5: at least one severity filter must be present
        boolean hasSeverity = resolved.stream().anyMatch(f -> f instanceof SeverityFilter);
        if (!hasSeverity) {
            defaults.stream()
                    .filter(f -> f instanceof SeverityFilter)
                    .forEach(resolved::add);
            logger.info("No severity filters found in storage. Added default severities.");
        }

        // Requirement 4: at least one custom-state filter must be present.
        // Only enforce when a provider is registered; without one we cannot distinguish
        // "no custom states in storage" from "custom states not yet resolvable".
        boolean hasCustomState = resolved.stream().anyMatch(f -> !(f instanceof SeverityFilter));
        if (!hasCustomState && providerAvailable) {
            defaults.stream()
                    .filter(f -> !(f instanceof SeverityFilter))
                    .forEach(resolved::add);
            logger.info("No custom-state filters found in storage. Added default custom states.");
        }

        // CRITICAL: Only overwrite filterValues when the provider is registered.
        // If we update filterValues during a no-provider resolution, the custom-state
        // strings are permanently dropped and cannot be recovered on the next restart.
        if (providerAvailable) {
            filterValues = toFilterValues(resolved);
            logger.info("Filters fully resolved from storage. Count: " + filterValues.size());
        } else {
            logger.info("Partial resolution (no provider yet). Resolved: " + resolved.size()
                    + ". filterValues preserved intact for full resolution later.");
        }

        resolvedWithProvider = providerAvailable;
        return resolved;
    }

    /**
     * Ensures that after a user-driven filter change the persisted set always contains
     * at least one default-severity <em>and</em> at least one default-custom-state entry
     * (requirement 6). Called by {@link #setFilters(Set)} after accepting the new selection.
     */
    private void ensureMinimumFiltersPresent() {
        Set<Filterable> defaults = FilterProviderRegistry.getInstance().getDefaultFilters();

        boolean hasSeverity = filters.stream().anyMatch(f -> f instanceof SeverityFilter);
        if (!hasSeverity) {
            defaults.stream()
                    .filter(f -> f instanceof SeverityFilter)
                    .forEach(filters::add);
            logger.info("Enforced minimum: no severities selected – added defaults.");
        }

        boolean hasCustomState = filters.stream().anyMatch(f -> !(f instanceof SeverityFilter));
        if (!hasCustomState) {
            defaults.stream()
                    .filter(f -> !(f instanceof SeverityFilter))
                    .forEach(filters::add);
            logger.info("Enforced minimum: no custom states selected – added defaults.");
        }
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
