package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.intellij.ast.window.actions.CxToolWindowAction;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public abstract class GroupByBaseAction extends ToggleAction implements CxToolWindowAction {

    private boolean selected = isPersistedOrDefault();

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

    /**
     * Determines the initial selected state from persisted settings.
     * Falls back to {@link GroupBy#DEFAULT_GROUP_BY} if nothing is persisted yet.
     */
    private boolean isPersistedOrDefault() {
        Set<String> persisted = GlobalSettingsState.getInstance().getGroupByValues();
        if (persisted == null || persisted.isEmpty()) {
            return GroupBy.DEFAULT_GROUP_BY.contains(getGroupBy());
        }
        return persisted.contains(getGroupBy().name());
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
