package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.checkmarx.intellij.tool.window.results.tree.GroupBy;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class GroupByBaseAction extends ToggleAction implements CxToolWindowAction {


    private boolean selected = GroupBy.DEFAULT_GROUP_BY.contains(getGroupBy());

    public GroupByBaseAction(@NotNull Supplier<@NlsActions.ActionText String> dynamicText) {
        super(dynamicText);
    }

    @Override
    public final boolean isSelected(@NotNull AnActionEvent e) {
        return selected;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        selected = state;
        Optional.ofNullable(getCxToolWindowPanel(e))
                .ifPresent(cxToolWindowPanel -> cxToolWindowPanel.changeGroupBy(getGroupBy(), state));

    }

    protected abstract GroupBy getGroupBy();
}
