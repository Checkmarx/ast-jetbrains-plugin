package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DynamicFilterActionGroup extends ActionGroup {
    static String IGNORE_LABEL = "IGNORED";
    static String NOT_IGNORE_LABEL = "NOT_IGNORED";
    public static String NOT_EXPLOITABLE_LABEL = "NOT_EXPLOITABLE";
    public static String PROPOSED_NOT_EXPLOITABLE_LABEL = "PROPOSED_NOT_EXPLOITABLE";

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
     * For quick membership checks, collect just the labels from DEFAULT_STATES.
     */
    private static final Set<String> DEFAULT_LABELS = STATES.stream()
            .map(CustomResultState::getLabel)
            .collect(Collectors.toUnmodifiableSet());

    private static List<CustomStateFilter> customStateFilters;

    @Override
    public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e) {
        // Lazily build filters only once
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        return customStateFilters.toArray(new CustomStateFilter[0]);
    }

    public static List<String> getStatesNameListForSastTriage(){
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        List<String> stateNameList = new ArrayList<>();
        for (CustomStateFilter customStateFilter : customStateFilters) {
            stateNameList.add(customStateFilter.getFilterable().getFilterValue());
        }

        // Remove SCA states from the list
        return stateNameList.stream().filter(s -> !s.equals(IGNORE_LABEL) && !s.equals(NOT_IGNORE_LABEL))
                .collect(Collectors.toList());
    }

    public static void refreshCustomStateFilters() {
        customStateFilters = buildCustomStateFilters();
    }

    /**
     * Builds a list of CustomStateFilter actions, including:
     *  - A filter for each DEFAULT_STATE
     *  - Filters for any "custom" states returned by the triageGetStates call,
     *    excluding those already in DEFAULT_STATES by label.
     */
    private static List<CustomStateFilter> buildCustomStateFilters() {
        List<CustomStateFilter> filters = new ArrayList<>();

        // 1. Add default states as filters
        for (CustomResultState state : STATES) {
            filters.add(new CustomStateFilter(new CustomResultState(state.getLabel(), state.getName())));
        }

        // 2. Attempt to retrieve additional states
        try {
            var cxWrapper = CxWrapperFactory.build(
                    GlobalSettingsState.getInstance(),
                    GlobalSettingsSensitiveState.getInstance()
            );
            var customStates = cxWrapper.triageGetStates(false);

            // Only add states that do not match any default label
            customStates.stream()
                    .map(CustomState::getName) // 'state' is from triage, use its "name" as label
                    .filter(label -> !DEFAULT_LABELS.contains(label))
                    .map(label -> new CustomStateFilter(new CustomResultState(label)))
                    .forEach(filters::add);

        } catch (Exception ignored) {
            // If we can't get custom states, just continue with defaults
        }

        return filters;
    }
}
