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
    public static final Set<Filterable> DEFAULT_STATES = Set.of(new CustomResultState("CONFIRMED"),
            new CustomResultState("TO_VERIFY"),
            new CustomResultState("URGENT"),
            new CustomResultState("NOT_EXPLOITABLE"),
            new CustomResultState("PROPOSED_NOT_EXPLOITABLE"),
            new CustomResultState("IGNORED"),
            new CustomResultState("NOT_IGNORED"));

    @Override
    public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e) {
        try{
            if (customStates.isEmpty())
            {
                customStates = CxWrapperFactory.build(GlobalSettingsState.getInstance(),
                        GlobalSettingsSensitiveState.getInstance())
                        .triageGetStates(false).stream()
                        .map(FilterBaseAction.CustomStateFilter::new).collect(Collectors.toList());
            }
        }catch (Exception ex){
            customStates = new ArrayList<>();
        }

        // Retrieve your custom filters from a service, configuration, or any runtime source.
        return customStates.toArray(FilterBaseAction[]::new);
    }
}
