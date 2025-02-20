package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DynamicFilterActionGroup extends ActionGroup {

    public static final Set<CustomResultState> DEFAULT_STATES = Set.of(
            new CustomResultState("CONFIRMED", "Confirmed"),
            new CustomResultState("TO_VERIFY", "To Verify"),
            new CustomResultState("URGENT", "Urgent"),
            new CustomResultState("NOT_EXPLOITABLE", "Not Exploitable"),
            new CustomResultState("PROPOSED_NOT_EXPLOITABLE", "Proposed Not Exploitable"),
            new CustomResultState("IGNORED", "Ignored"),
            new CustomResultState("NOT_IGNORED", "Not Ignored"));

    private List<CustomStateFilter> customStateFilters;

    @Override
    public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e) {
        if (customStateFilters == null) {
            customStateFilters = buildCustomStateFilters();
        }
        return customStateFilters.toArray(new CustomStateFilter[0]);
    }

    private List<CustomStateFilter> buildCustomStateFilters() {
        List<CustomStateFilter> filters = new ArrayList<>();

        // Add default states as filters.
        DEFAULT_STATES.forEach(state ->
                filters.add(new CustomStateFilter(state.getLabel(), state.getName()))
        );

        try {
            var globalWrapper = CxWrapperFactory.build(
                    GlobalSettingsState.getInstance(),
                    GlobalSettingsSensitiveState.getInstance());
            var customStates = globalWrapper.triageGetStates(false);

            // Exclude states that are already present in DEFAULT_STATES.
            customStates.stream()
                    .filter(customState -> DEFAULT_STATES.stream()
                            .noneMatch(defaultState -> defaultState.getLabel().equals(customState.getName())))
                    .map(state -> new CustomStateFilter(state.getName()))
                    .forEach(filters::add);
        } catch (Exception ex) {
            // TODO: Log the exception appropriately.
            // For now, we fall back to only the default filters.
        }
        return filters;
    }
}
