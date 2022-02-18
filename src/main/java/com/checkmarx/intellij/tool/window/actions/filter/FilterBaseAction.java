package com.checkmarx.intellij.tool.window.actions.filter;

import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.ResultState;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for severity filters.
 * All filters share the same behaviour, simply changing which severity they correspond to.
 * Extending filters only override getSeverity method to relate to a severity.
 */
public abstract class FilterBaseAction extends ToggleAction implements CxToolWindowAction {

    public static final Topic<FilterChanged> FILTER_CHANGED = Topic.create("Filter Changed", FilterChanged.class);

    private final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    private final Filterable filterable = getFilterable();

    public FilterBaseAction() {
        super();
        getTemplatePresentation().setText(filterable.tooltipSupplier());
        getTemplatePresentation().setIcon(filterable.getIcon());
    }

    /**
     * {@inheritDoc}
     * Checks global settings for selection
     */
    @Override
    public final boolean isSelected(@NotNull AnActionEvent e) {
        return GlobalSettingsState.getInstance().getFilters().contains(filterable);
    }

    /**
     * {@inheritDoc}
     * Store state in the global settings and send a message on the {@link FilterBaseAction#FILTER_CHANGED} topic.
     * All subscribing tool windows will redraw the results tree with the new filters.
     */
    @Override
    public final void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (state) {
            GlobalSettingsState.getInstance().getFilters().add(filterable);
        } else {
            GlobalSettingsState.getInstance().getFilters().remove(filterable);
        }
        messageBus.syncPublisher(FILTER_CHANGED).filterChanged();
    }

    /**
     * @return severity for the extending filter
     */
    protected abstract Filterable getFilterable();

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

    public static class ConfirmedFilter extends FilterBaseAction {

        public ConfirmedFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.CONFIRMED;
        }
    }

    public static class UrgentFilter extends FilterBaseAction {

        public UrgentFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.URGENT;
        }
    }

    public static class ToVerifyFilter extends FilterBaseAction {

        public ToVerifyFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.TO_VERIFY;
        }
    }

    public static class NotExploitableFilter extends FilterBaseAction {

        public NotExploitableFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.NOT_EXPLOITABLE;
        }
    }

    public static class ProposedNotExploitableFilter extends FilterBaseAction {

        public ProposedNotExploitableFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.PROPOSED_NOT_EXPLOITABLE;
        }
    }

    public static class IgnoredFilter extends FilterBaseAction {

        public IgnoredFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.IGNORED;
        }
    }

    public static class NotIgnoredFilter extends FilterBaseAction {

        public NotIgnoredFilter() {
            super();
        }

        @Override
        protected Filterable getFilterable() {
            return ResultState.NOT_IGNORED;
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
}
