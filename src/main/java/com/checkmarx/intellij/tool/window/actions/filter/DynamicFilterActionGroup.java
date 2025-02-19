package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.intellij.openapi.actionSystem.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DynamicFilterActionGroup extends ActionGroup {

    @Getter
    static List<FilterBaseAction.CustomStateFilter> customStates = new ArrayList<>();
    public static final Set<CustomResultState> DEFAULT_STATES = Set.of(new CustomResultState("CONFIRMED", "Confirmed"),
            new CustomResultState("TO_VERIFY", "To Verify"),
            new CustomResultState("URGENT", "Urgent"),
            new CustomResultState("NOT_EXPLOITABLE", "Not Exploitable"),
            new CustomResultState("PROPOSED_NOT_EXPLOITABLE", "Proposed Not Exploitable"),
            new CustomResultState("IGNORED", "Ignored"),
            new CustomResultState("NOT_IGNORED", "Not Ignored"));

    @Override
    public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e) {
        try {
            if (customStates.isEmpty()) {
                customStates = DEFAULT_STATES.stream().map((cs) -> new FilterBaseAction.CustomStateFilter(cs.getLabel(), cs.getName())).collect(Collectors.toList());
                customStates.addAll(CxWrapperFactory.build(GlobalSettingsState.getInstance(),
                                GlobalSettingsSensitiveState.getInstance())
                        .triageGetStates(false).stream().filter((cs) -> DEFAULT_STATES.stream().noneMatch((ds) -> ds.getLabel().equals(cs.getName())))
                        .map((cs) -> new FilterBaseAction.CustomStateFilter(cs.getName())).collect(Collectors.toList()));
            }
        } catch (Exception ex) {
            customStates = new ArrayList<>();
        }

        // Retrieve your custom filters from a service, configuration, or any runtime source.
        return customStates.toArray(FilterBaseAction[]::new);
    }
}
