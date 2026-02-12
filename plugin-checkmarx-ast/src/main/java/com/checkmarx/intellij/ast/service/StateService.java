package com.checkmarx.intellij.ast.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.common.settings.FilterProvider;
import com.checkmarx.intellij.common.settings.FilterProviderRegistry;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.ast.results.CustomResultState;
import com.checkmarx.intellij.ast.window.actions.filter.CustomStateFilter;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.common.window.actions.filter.SeverityFilter;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.checkmarx.intellij.common.utils.Constants.*;

/**
 * StateService manages custom result states and provides default filters.
 * Implements FilterProvider to supply default filters to GlobalSettingsState.
 */
public class StateService implements FilterProvider {
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
        } catch (Exception ignored) {
            // If custom states cannot be retrieved, continue with default filters.
        }

        return filters;
    }

    /**
     * Implementation of the FilterProvider interface.
     * Returns the default set of filters, including severity filters and custom state filters.
     *
     * @return Set of default Filterable objects
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
        return filters;
    }
}
