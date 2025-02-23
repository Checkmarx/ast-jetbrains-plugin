package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.intellij.service.StateService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class DynamicFilterActionGroup extends ActionGroup {

    @Override
    public AnAction @NotNull [] getChildren(@NotNull AnActionEvent e) {
        return StateService.getCustomStateFilters().toArray(new CustomStateFilter[0]);
    }
}
