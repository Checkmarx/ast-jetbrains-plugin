package com.checkmarx.intellij.ast.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.window.actions.filter.CustomStateFilter;
import com.checkmarx.intellij.common.settings.FilterProvider;
import com.checkmarx.intellij.common.settings.FilterProviderRegistry;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.openapi.diagnostic.Logger;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.checkmarx.intellij.common.utils.Constants.*;
import static java.lang.String.format;

/**
 * StateService manages custom result states and provides default filters.
 * Implements FilterProvider to supply default filters to GlobalSettingsState.
 */
public class StateService implements FilterProvider {

    private final Logger logger = Utils.getLogger(StateService.class);

    @Getter
    private final Set<CustomResultState> states;
    @Getter
    private final Set<String> defaultLabels;
    private List<CustomStateFilter> customStateFilters;

    // Private constructor prevents instantiation from other classes.
    private StateService() {
       // Keeping natural order
        this.states = Stream.of(
                new CustomResultState(CONFIRMED, "Confirmed"),
                new CustomResultState(IGNORE_LABEL, "Ignored"),
                new CustomResultState(NOT_EXPLOITABLE_LABEL, "Not Exploitable"),
                new CustomResultState(NOT_IGNORE_LABEL, "Not Ignored"),
                new CustomResultState(PROPOSED_NOT_EXPLOITABLE_LABEL, "Proposed Not Exploitable"),
                new CustomResultState(SCA_HIDE_DEV_TEST_DEPENDENCIES, "SCA Hide Dev && Test Dependencies"),
                new CustomResultState(TO_VERIFY, "To Verify"),
                new CustomResultState(URGENT, "Urgent")
        ).sorted(Comparator.comparing(CustomResultState::getLabel)).collect(Collectors.toCollection(LinkedHashSet::new));

        this.defaultLabels = states.stream()
                .map(CustomResultState::getLabel)
                .collect(Collectors.toUnmodifiableSet());

        // Register this StateService as the FilterProvider for GlobalSettingsState
        FilterProviderRegistry.getInstance().registerProvider(this);
    }

    // Eagerly create the singleton instance.
    private static final StateService INSTANCE = new StateService();

    // Public accessor for the singleton instance.
    public static StateService getInstance() {
        return INSTANCE;
    }

    public List<CustomStateFilter> getCustomStateFilters() {
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        return customStateFilters;
    }

    public List<String> getStatesNameListForSastTriage() {
        return getCustomStateFilters().stream()
                .map(filter -> filter.getFilterable().getFilterValue())
                .filter(s -> !s.equals(IGNORE_LABEL) && !s.equals(NOT_IGNORE_LABEL)
                        && !s.equals(SCA_HIDE_DEV_TEST_DEPENDENCIES)) // exclude from the triage
                .collect(Collectors.toList());
    }

    public void refreshCustomStateFilters() {
        customStateFilters = buildCustomStateFilters();
    }

    /**
     * Removes any persisted custom-state filter values that no longer exist on the server.
     *
     * <p>Called once after a successful login. Fetches the current list of states from the
     * server via {@code triageGetStates} and compares it against the persisted
     * {@code filterValues} in {@link GlobalSettingsState}. Any value that is:
     * <ul>
     *   <li>not a {@link SeverityFilter} severity, AND</li>
     *   <li>not one of the 8 hardcoded predefined states, AND</li>
     *   <li>not present in the server's current state list</li>
     * </ul>
     * is silently dropped from the persisted selection.
     *
     * <p>If the server call fails the persisted values are left unchanged.
     */
    public void pruneStaleCustomStates() {
        GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
        Set<String> currentFilterValues = settingsState.getFilterValues();

        if (currentFilterValues == null || currentFilterValues.isEmpty()) {
            return;
        }

        Set<String> validServerLabels;
        try {
            var cxWrapper = CxWrapperFactory.build(
                    settingsState,
                    GlobalSettingsSensitiveState.getInstance()
            );
            validServerLabels = cxWrapper.triageGetStates(false).stream()
                    .map(CustomState::getName)
                    .collect(Collectors.toSet());
        } catch (Exception exception) {
            logger.debug("Failed to retrieve server states for stale-filter pruning. Skipping.", exception);
            return;
        }

        // A filter value is valid if it is a severity, a predefined state, or exists on the server
        Set<String> pruned = currentFilterValues.stream()
                .filter(value -> isSeverityValue(value) || defaultLabels.contains(value) || validServerLabels.contains(value))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (pruned.size() < currentFilterValues.size()) {
            Set<String> removed = new LinkedHashSet<>(currentFilterValues);
            removed.removeAll(pruned);
            logger.info("Pruned stale custom state filters no longer present on server: " + removed);
            settingsState.setFilterValues(pruned);
            // Reset runtime filter cache so the pruned values take effect on next access
            settingsState.setFilters(null);
        }
    }

    /**
     * Returns true if the given filter value corresponds to a {@link SeverityFilter} enum constant.
     */
    private boolean isSeverityValue(String value) {
        try {
            SeverityFilter.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Builds a list of CustomStateFilter actions, including:
     * - A filter for each default state.
     * - Filters for any "custom" states returned by the triageGetStates call,
     * excluding those already present in the default states.
     */
    private List<CustomStateFilter> buildCustomStateFilters() {
        List<CustomStateFilter> filters = states.stream()
                .map(state -> new CustomStateFilter(new CustomResultState(state.getLabel(), state.getName())))
                .collect(Collectors.toList());

        try {
            var cxWrapper = CxWrapperFactory.build(
                    GlobalSettingsState.getInstance(),
                    GlobalSettingsSensitiveState.getInstance()
            );
            var customStates = cxWrapper.triageGetStates(false);

            customStates.stream()
                    .map(CustomState::getName)
                    .filter(label -> !defaultLabels.contains(label))
                    .map(label -> new CustomStateFilter(new CustomResultState(label)))
                    .forEach(filters::add);
        } catch (Exception exception) {
            // If custom states cannot be retrieved, continue with default filters.
            logger.debug("Failed to retrieve custom states.", exception);
        }

        return filters;
    }

    /**
     * Implementation of the FilterProvider interface.
     * Returns the default set of filters, including severity filters and custom state filters.
     * 
     * This method is called on:
     * 1. First IDE launch (when no filters are persisted)
     * 2. When restoring persisted state fails
     * 3. During filter initialization in GlobalSettingsState
     * 
     * The returned filters are always persisted to ensure:
     * - User selections are remembered across IDE restarts
     * - Default state/severity filters are applied on first launch
     * - Custom filters can be added from the server
     *
     * @return Set of default Filterable objects (includes SeverityFilter + CustomResultState objects)
     */
    @Override
    public Set<Filterable> getDefaultFilters() {
        Set<Filterable> filters = new HashSet<>(SeverityFilter.DEFAULT_SEVERITIES);
        filters.addAll(
                states.stream()
                        .filter(s -> !s.getLabel().equals(NOT_EXPLOITABLE_LABEL)
                                && !s.getLabel().equals(PROPOSED_NOT_EXPLOITABLE_LABEL)
                        && !s.getLabel().equals(SCA_HIDE_DEV_TEST_DEPENDENCIES)) // excluding from default filter
                        .collect(Collectors.toSet())
        );
        logger.debug(format("Default filters initialized. Total count: %s, (Severity filters: %s, + Custom state filters)",
                filters.size(), SeverityFilter.DEFAULT_SEVERITIES.size() ));
        return filters;
    }

    /**
     * Resolves a persisted filter value string back to the matching Filterable instance.
     * Checks SeverityFilter enum values first, then known custom states,
     * and finally creates a new CustomResultState for unknown server-side states.
     *
     * @param filterValue the persisted value from {@link Filterable#getFilterValue()}
     * @return Optional containing the resolved Filterable, never empty (falls back to new CustomResultState)
     */
    @Override
    public Optional<Filterable> resolveFilterByValue(String filterValue) {
        // Try SeverityFilter first
        try {
            return Optional.of(SeverityFilter.valueOf(filterValue));
        } catch (IllegalArgumentException ignored) {
            // Not a severity filter, continue
        }
        // Try known custom states by label
        return states.stream()
                .filter(s -> s.getLabel().equals(filterValue))
                .map(s -> (Filterable) s)
                .findFirst()
                // For unknown (server-side) custom states, create on the fly
                .or(() -> Optional.of(new CustomResultState(filterValue)));
    }
}
