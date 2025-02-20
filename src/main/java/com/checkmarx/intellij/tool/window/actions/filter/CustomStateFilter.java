package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CustomStateFilter extends FilterBaseAction {

    public CustomStateFilter(String name) {
        super(new CustomResultState(name));
    }

    public CustomStateFilter(String label, String name) {
        super(new CustomResultState(label, name));
    }

    @Override
    protected Filterable getFilterable() {
        return filterable;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return GlobalSettingsState.getInstance()
                .getFilters()
                .stream()
                .anyMatch(this::isMatchingLabel);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        Set<Filterable> filters = GlobalSettingsState.getInstance().getFilters();

        if (state && filters.stream().noneMatch(this::isMatchingLabel)) {
            filters.add(filterable);
        } else {
            filters.removeIf(this::isMatchingLabel);
        }

        // Notify listeners
        messageBus.syncPublisher(FILTER_CHANGED).filterChanged();
    }

    /**
     * Checks if the given Filterable matches this filter's label.
     */
    private boolean isMatchingLabel(Filterable candidate) {
        if (candidate instanceof CustomResultState && filterable instanceof CustomResultState) {
            String candidateLabel = ((CustomResultState) candidate).getLabel();
            String thisLabel      = ((CustomResultState) filterable).getLabel();
            return candidateLabel.equals(thisLabel);
        }
        return false;
    }
}
