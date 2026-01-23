package com.checkmarx.intellij.service;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.checkmarx.intellij.Constants.*;

public class StateService {
    @Getter
    private final Set<CustomResultState> states;
    @Getter
    private final Set<String> defaultLabels;
    private List<CustomResultState> customResultStates;

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

    }

    // Eagerly create the singleton instance.
    private static final StateService INSTANCE = new StateService();

    // Public accessor for the singleton instance.
    public static StateService getInstance() {
        return INSTANCE;
    }

    public List<CustomResultState> getCustomResultStates() {
        if (customResultStates == null) {
            customResultStates = buildCustomResultStates();
        }
        return customResultStates;
    }

    public List<String> getStatesNameListForSastTriage() {
        return getCustomResultStates().stream()
                .map(CustomResultState::getFilterValue)
                .filter(s -> !s.equals(IGNORE_LABEL) && !s.equals(NOT_IGNORE_LABEL)
                        && !s.equals(SCA_HIDE_DEV_TEST_DEPENDENCIES)) // exclude from the triage
                .collect(Collectors.toList());
    }

    public void refreshCustomResultStates() {
        customResultStates = buildCustomResultStates();
    }

    /**
     * Builds a list of CustomResultState objects, including:
     * - Each default state.
     * - Any "custom" states returned by the triageGetStates call,
     * excluding those already present in the default states.
     */
    private List<CustomResultState> buildCustomResultStates() {
        List<CustomResultState> resultStates = states.stream()
                .map(state -> new CustomResultState(state.getLabel(), state.getName()))
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
                    .map(CustomResultState::new)
                    .forEach(resultStates::add);
        } catch (Exception ignored) {
            // If custom states cannot be retrieved, continue with default states.
        }

        return resultStates;
    }

    public Set<Filterable> getDefaultFilters() {
        Set<Filterable> filters = new HashSet<>(Severity.DEFAULT_SEVERITIES);
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
