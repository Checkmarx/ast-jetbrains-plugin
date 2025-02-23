package com.checkmarx.intellij.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.checkmarx.intellij.tool.window.actions.filter.CustomStateFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StateService {
    public static final String IGNORE_LABEL = "IGNORED";
    public static final String NOT_IGNORE_LABEL = "NOT_IGNORED";
    public static final String NOT_EXPLOITABLE_LABEL = "NOT_EXPLOITABLE";
    public static final String PROPOSED_NOT_EXPLOITABLE_LABEL = "PROPOSED_NOT_EXPLOITABLE";

    /**
     * Default states always present in the filter list.
     */
    public static final Set<CustomResultState> STATES = Set.of(
            new CustomResultState("CONFIRMED", "Confirmed"),
            new CustomResultState("TO_VERIFY", "To Verify"),
            new CustomResultState("URGENT", "Urgent"),
            new CustomResultState(NOT_EXPLOITABLE_LABEL, "Not Exploitable"),
            new CustomResultState(PROPOSED_NOT_EXPLOITABLE_LABEL, "Proposed Not Exploitable"),
            new CustomResultState(IGNORE_LABEL, "Ignored"),
            new CustomResultState(NOT_IGNORE_LABEL, "Not Ignored")
    );

    /**
     * For quick membership checks, collect just the labels from STATES.
     */
    private static final Set<String> DEFAULT_LABELS = STATES.stream()
            .map(CustomResultState::getLabel)
            .collect(Collectors.toUnmodifiableSet());

    private static List<CustomStateFilter> customStateFilters;

    public static List<CustomStateFilter> getCustomStateFilters() {
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        return customStateFilters;
    }

    public static List<String> getStatesNameListForSastTriage() {
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        List<String> stateNameList = new ArrayList<>();
        for (CustomStateFilter filter : customStateFilters) {
            stateNameList.add(filter.getFilterable().getFilterValue());
        }
        // Remove SCA states from the list
        return stateNameList.stream()
                .filter(s -> !s.equals(IGNORE_LABEL) && !s.equals(NOT_IGNORE_LABEL))
                .collect(Collectors.toList());
    }

    public static void refreshCustomStateFilters() {
        customStateFilters = buildCustomStateFilters();
    }

    /**
     * Builds a list of CustomStateFilter actions, including:
     *  - A filter for each default state.
     *  - Filters for any "custom" states returned by the triageGetStates call,
     *    excluding those already present in the default states.
     */
    private static List<CustomStateFilter> buildCustomStateFilters() {
        List<CustomStateFilter> filters = new ArrayList<>();

        // 1. Add default state filters.
        for (CustomResultState state : STATES) {
            filters.add(new CustomStateFilter(new CustomResultState(state.getLabel(), state.getName())));
        }

        // 2. Attempt to retrieve additional custom states.
        try {
            var cxWrapper = CxWrapperFactory.build(
                    GlobalSettingsState.getInstance(),
                    GlobalSettingsSensitiveState.getInstance()
            );
            var customStates = cxWrapper.triageGetStates(false);

            // Only add states that do not match any default label.
            customStates.stream()
                    .map(CustomState::getName)
                    .filter(label -> !DEFAULT_LABELS.contains(label))
                    .map(label -> new CustomStateFilter(new CustomResultState(label)))
                    .forEach(filters::add);
        } catch (Exception ignored) {
            // If custom states cannot be retrieved, continue with default filters.
        }

        return filters;
    }
}
