package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.ast.predicate.CustomState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Base class for severity filters.
 * All filters share the same behaviour, simply changing which severity they correspond to.
 * Extending filters only override getSeverity method to relate to a severity.
 */
public abstract class FilterBaseAction extends ToggleAction implements CxToolWindowAction {

    public static final Topic<FilterChanged> FILTER_CHANGED = Topic.create("Filter Changed", FilterChanged.class);

    private final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    protected final Filterable filterable;

    public FilterBaseAction() {
        super();
        filterable = getFilterable();
        getTemplatePresentation().setText(filterable.tooltipSupplier());
        getTemplatePresentation().setIcon(filterable.getIcon());
    }

    public FilterBaseAction(CustomResultState filterable) {
        super();
        this.filterable = filterable;
        getTemplatePresentation().setText(filterable.getName());
        getTemplatePresentation().setIcon(filterable.getIcon());
    }

    /**
     * {@inheritDoc}
     * Checks global settings for selection
     */
    @Override
    public final boolean isSelected(@NotNull AnActionEvent e) {
        Set<Filterable> filters = GlobalSettingsState.getInstance().getFilters();
        if (filterable instanceof CustomResultState) {
            return filters.stream()
                    .anyMatch(f -> f instanceof CustomResultState && ((CustomResultState) f).getLabel().equals(((CustomResultState) filterable).getLabel()));
        }
        return GlobalSettingsState.getInstance().getFilters().contains(filterable);
    }

    /**
     * {@inheritDoc}
     * Store state in the global settings and send a message on the {@link FilterBaseAction#FILTER_CHANGED} topic.
     * All subscribing tool windows will redraw the results tree with the new filters.
     */
    @Override
    public final void setSelected(@NotNull AnActionEvent e, boolean state) {
        Set<Filterable> filters = GlobalSettingsState.getInstance().getFilters();
        if (state) {
            if (filterable instanceof CustomResultState && filters.stream().noneMatch(f -> f instanceof CustomResultState && ((CustomResultState) f).getLabel().equals(((CustomResultState) filterable).getLabel()))) {
                filters.add(filterable);
            } else if (!(filterable instanceof CustomResultState)) {
                filters.add(filterable);
            }
        } else {
            filters.removeIf((f)->{
                if (f instanceof CustomResultState){
                    return ((CustomResultState) f).getLabel().equals(((CustomResultState) filterable).getLabel());
                } else {
                    return false;
                }
            });
        }
        messageBus.syncPublisher(FILTER_CHANGED).filterChanged();
    }

    /**
     * @return severity for the extending filter
     */
    protected abstract Filterable getFilterable();

    public static class CriticalFilter extends FilterBaseAction {

        public CriticalFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return Severity.CRITICAL;
        }
    }

    public static class HighFilter extends FilterBaseAction {

        public HighFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return Severity.HIGH;
        }
    }

    public static class MediumFilter extends FilterBaseAction {

        public MediumFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return Severity.MEDIUM;
        }
    }

    public static class LowFilter extends FilterBaseAction {

        public LowFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return Severity.LOW;
        }
    }

    public static class InfoFilter extends FilterBaseAction {

        public InfoFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return Severity.INFO;
        }
    }

    public static class CustomStateFilter extends FilterBaseAction {
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
    }

    /**
     * Interface for topic {@link FilterBaseAction#FILTER_CHANGED}.
     */
    public interface FilterChanged {
        /**
         * Method called when a filter is clicked.
         */
        void filterChanged();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
