package com.checkmarx.intellij.ignite.ui.actions;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.CxIcons;
import com.checkmarx.intellij.common.devassist.ui.findings.window.CxIgnoredFindings;
import com.checkmarx.intellij.common.devassist.utils.ScanEngine;
import com.checkmarx.intellij.common.window.Severity;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Toolbar actions for the Ignored Findings tab in the Checkmarx tool window.
 *
 * <p>This class provides:
 * <ul>
 *   <li><b>Severity filters</b> - Toggle buttons for MALICIOUS, CRITICAL, HIGH, MEDIUM, LOW</li>
 *   <li><b>Type filter dropdown</b> - Filter by vulnerability type (SAST, SCA, Secrets, IaC, Containers)</li>
 *   <li><b>Sort dropdown</b> - Sort by severity or last updated date</li>
 * </ul>
 *
 * <p>Uses independent state from CxFindingsWindow to avoid cross-tab filter interference.
 * State is managed by singleton instances: {@link TypeFilterState}, {@link SortState},
 * and {@link IgnoredFindingsSeverityFilterState}.
 *
 * @see CxIgnoredFindings
 */
public class IgnoredFindingsToolbarActions {

    // ========== Message Topics for Filter/Sort Changes ==========

    /** Topic for vulnerability type filter changes. */
    public static final Topic<TypeFilterChanged> TYPE_FILTER_TOPIC =
            Topic.create("Type Filter Changed", TypeFilterChanged.class);

    /** Topic for sort order changes. */
    public static final Topic<SortChanged> SORT_TOPIC =
            Topic.create("Sort Changed", SortChanged.class);

    /** Topic for severity filter changes (independent from CxFindingsWindow). */
    public static final Topic<SeverityFilterChanged> SEVERITY_FILTER_TOPIC =
            Topic.create("Ignored Findings Severity Filter Changed", SeverityFilterChanged.class);

    // ========== Filter Dropdown ==========

    /** Dropdown showing vulnerability type filter options (SAST, SCA, Secrets, etc.) */
    public static class VulnerabilityTypeFilterDropdown extends ActionGroup {
        public VulnerabilityTypeFilterDropdown() {
            getTemplatePresentation().setText(Bundle.message(Resource.IGNORED_FILTER));
            getTemplatePresentation().setIcon(CxIcons.Ignored.FILTER);
            setPopup(true);
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                Separator.create(Bundle.message(Resource.IGNORED_VULNERABILITY_TYPE)),
                new SASTTypeFilter(),
                new SCATypeFilter(),
                new SecretsTypeFilter(),
                new ContainersTypeFilter(),
                new IaCTypeFilter()
            };
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    // ========== Sort Dropdown ==========

    /** Dropdown showing sort options (severity, date order) */
    public static class SortDropdown extends ActionGroup {
        public SortDropdown() {
            getTemplatePresentation().setText(Bundle.message(Resource.IGNORED_SORT));
            getTemplatePresentation().setDescription(Bundle.message(Resource.IGNORED_SORT));
            getTemplatePresentation().setIcon(CxIcons.Ignored.SORT);
            setPopup(true);
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                Separator.create(Bundle.message(Resource.IGNORED_SORT_BY)),
                new SortBySeverityHighToLow(),
                new SortBySeverityLowToHigh(),
                new SortByLastUpdated(),
                Separator.create(),
                Separator.create(Bundle.message(Resource.IGNORED_ORDER_BY)),
                new SortByLastUpdatedOldestFirst(),
                new SortByLastUpdatedNewestFirst()
            };
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    // ========== Vulnerability Type Filters ==========

    /** Base class for vulnerability type filter toggles */
    public abstract static class VulnerabilityTypeFilter extends ToggleAction {
        protected final ScanEngine engineType;

        public VulnerabilityTypeFilter(String text, Icon icon, ScanEngine engineType) {
            super(text, null, icon);
            this.engineType = engineType;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return TypeFilterState.getInstance().isSelected(engineType);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            TypeFilterState.getInstance().setSelected(engineType, state);
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(TYPE_FILTER_TOPIC).filterChanged();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    public static class SASTTypeFilter extends VulnerabilityTypeFilter {
        public SASTTypeFilter() { super(Bundle.message(Resource.IGNORED_FILTER_SAST), CxIcons.Ignored.ENGINE_CHIP_SAST, ScanEngine.ASCA); }
    }
    public static class SCATypeFilter extends VulnerabilityTypeFilter {
        public SCATypeFilter() { super(Bundle.message(Resource.IGNORED_FILTER_SCA), CxIcons.Ignored.ENGINE_CHIP_SCA, ScanEngine.OSS); }
    }
    public static class SecretsTypeFilter extends VulnerabilityTypeFilter {
        public SecretsTypeFilter() { super(Bundle.message(Resource.IGNORED_FILTER_SECRETS), CxIcons.Ignored.ENGINE_CHIP_SECRETS, ScanEngine.SECRETS); }
    }
    public static class ContainersTypeFilter extends VulnerabilityTypeFilter {
        public ContainersTypeFilter() { super(Bundle.message(Resource.IGNORED_FILTER_CONTAINERS), CxIcons.Ignored.ENGINE_CHIP_CONTAINERS, ScanEngine.CONTAINERS); }
    }
    public static class IaCTypeFilter extends VulnerabilityTypeFilter {
        public IaCTypeFilter() { super(Bundle.message(Resource.IGNORED_FILTER_IAC), CxIcons.Ignored.ENGINE_CHIP_IAC, ScanEngine.IAC); }
    }

    // ========== Sort Actions ==========

    /** Primary sort field selection */
    public enum SortField {
        SEVERITY_HIGH_TO_LOW(Resource.IGNORED_SORT_SEVERITY_HIGH_LOW),
        SEVERITY_LOW_TO_HIGH(Resource.IGNORED_SORT_SEVERITY_LOW_HIGH),
        LAST_UPDATED(Resource.IGNORED_SORT_LAST_UPDATED);

        private final Resource resourceKey;
        SortField(Resource resourceKey) { this.resourceKey = resourceKey; }
        public String getDisplayName() { return Bundle.message(resourceKey); }
    }

    /** Date order selection (only applies when SortField is LAST_UPDATED) */
    public enum DateOrder {
        OLDEST_FIRST(Resource.IGNORED_ORDER_OLDEST_FIRST),
        NEWEST_FIRST(Resource.IGNORED_ORDER_NEWEST_FIRST);

        private final Resource resourceKey;
        DateOrder(Resource resourceKey) { this.resourceKey = resourceKey; }
        public String getDisplayName() { return Bundle.message(resourceKey); }
    }

    /** Base class for sort field toggle actions (Sort By section) */
    public abstract static class SortFieldAction extends ToggleAction {
        protected final SortField sortField;

        public SortFieldAction(SortField sortField) {
            super(sortField.getDisplayName());
            this.sortField = sortField;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return SortState.getInstance().getSortField() == sortField;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
                SortState.getInstance().setSortField(sortField);
                // Only trigger sort for severity options (immediate effect)
                // LAST_UPDATED requires a DateOrder selection to trigger sort
                if (sortField != SortField.LAST_UPDATED) {
                    ApplicationManager.getApplication().getMessageBus()
                            .syncPublisher(SORT_TOPIC).sortChanged();
                }
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    /** Date order toggle action (Order By section) - only enabled when LAST_UPDATED is selected */
    public abstract static class DateOrderAction extends ToggleAction {
        protected final DateOrder dateOrder;

        public DateOrderAction(DateOrder dateOrder) {
            super(dateOrder.getDisplayName());
            this.dateOrder = dateOrder;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return SortState.getInstance().getDateOrder() == dateOrder;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
                SortState.getInstance().setDateOrder(dateOrder);
                // Only trigger sort if LAST_UPDATED is the current sort field
                if (SortState.getInstance().getSortField() == SortField.LAST_UPDATED) {
                    ApplicationManager.getApplication().getMessageBus()
                            .syncPublisher(SORT_TOPIC).sortChanged();
                }
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            // Disable when not sorting by LAST_UPDATED
            boolean enabled = SortState.getInstance().getSortField() == SortField.LAST_UPDATED;
            e.getPresentation().setEnabled(enabled);
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    // Sort By actions (primary field selection)
    public static class SortBySeverityHighToLow extends SortFieldAction {
        public SortBySeverityHighToLow() { super(SortField.SEVERITY_HIGH_TO_LOW); }
    }
    public static class SortBySeverityLowToHigh extends SortFieldAction {
        public SortBySeverityLowToHigh() { super(SortField.SEVERITY_LOW_TO_HIGH); }
    }
    public static class SortByLastUpdated extends SortFieldAction {
        public SortByLastUpdated() { super(SortField.LAST_UPDATED); }
    }

    // Order By actions (date direction - only enabled when LAST_UPDATED is selected)
    public static class SortByLastUpdatedOldestFirst extends DateOrderAction {
        public SortByLastUpdatedOldestFirst() { super(DateOrder.OLDEST_FIRST); }
    }
    public static class SortByLastUpdatedNewestFirst extends DateOrderAction {
        public SortByLastUpdatedNewestFirst() { super(DateOrder.NEWEST_FIRST); }
    }

    // ========== Severity Filters (Independent State) ==========

    /** Base toggle action for severity filters - uses independent state from CxFindingsWindow */
    public abstract static class IgnoredFindingsSeverityFilter extends ToggleAction {
        protected final Filterable filterable;

        public IgnoredFindingsSeverityFilter() {
            filterable = getFilterable();
            getTemplatePresentation().setText(filterable.tooltipSupplier());
            getTemplatePresentation().setIcon(filterable.getIcon());
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return IgnoredFindingsSeverityFilterState.getInstance().isSelected(filterable);
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            IgnoredFindingsSeverityFilterState.getInstance().setSelected(filterable, state);
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SEVERITY_FILTER_TOPIC).filterChanged();
        }

        protected abstract Filterable getFilterable();

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    public static class IgnoredMaliciousFilter extends IgnoredFindingsSeverityFilter {
        @Override protected Filterable getFilterable() { return Severity.MALICIOUS; }
    }
    public static class IgnoredCriticalFilter extends IgnoredFindingsSeverityFilter {
        @Override protected Filterable getFilterable() { return Severity.CRITICAL; }
    }
    public static class IgnoredHighFilter extends IgnoredFindingsSeverityFilter {
        @Override protected Filterable getFilterable() { return Severity.HIGH; }
    }
    public static class IgnoredMediumFilter extends IgnoredFindingsSeverityFilter {
        @Override protected Filterable getFilterable() { return Severity.MEDIUM; }
    }
    public static class IgnoredLowFilter extends IgnoredFindingsSeverityFilter {
        @Override protected Filterable getFilterable() { return Severity.LOW; }
    }

    // ========== State Managers (Singleton Pattern) ==========

    /**
     * Singleton state manager for vulnerability type filters.
     * Tracks which scan engines (SAST, SCA, Secrets, etc.) are selected.
     * Thread-safe via synchronized set.
     */
    public static class TypeFilterState {
        private static final TypeFilterState INSTANCE = new TypeFilterState();
        private final Set<ScanEngine> selectedEngines = Collections.synchronizedSet(EnumSet.allOf(ScanEngine.class));

        private TypeFilterState() { selectedEngines.remove(ScanEngine.ALL); }

        public static TypeFilterState getInstance() { return INSTANCE; }

        public boolean isSelected(ScanEngine engine) { return selectedEngines.contains(engine); }

        public void setSelected(ScanEngine engine, boolean selected) {
            if (selected) selectedEngines.add(engine);
            else selectedEngines.remove(engine);
        }

        /** Returns a copy of currently selected engines. */
        public Set<ScanEngine> getSelectedEngines() { return new HashSet<>(selectedEngines); }

        /** Returns true if any engine is deselected (i.e., filtering is active). */
        public boolean hasActiveFilters() {
            Set<ScanEngine> allRealEngines = EnumSet.allOf(ScanEngine.class);
            allRealEngines.remove(ScanEngine.ALL);
            return !selectedEngines.containsAll(allRealEngines);
        }
    }

    /**
     * Singleton state manager for sort settings.
     * Tracks the current sort field and date order.
     */
    public static class SortState {
        private static final SortState INSTANCE = new SortState();
        private SortField sortField = SortField.SEVERITY_HIGH_TO_LOW;
        private DateOrder dateOrder = DateOrder.NEWEST_FIRST;

        private SortState() {}

        public static SortState getInstance() { return INSTANCE; }

        public SortField getSortField() { return sortField; }
        public void setSortField(SortField sortField) { this.sortField = sortField; }

        public DateOrder getDateOrder() { return dateOrder; }
        public void setDateOrder(DateOrder dateOrder) { this.dateOrder = dateOrder; }
    }

    /**
     * Singleton state manager for severity filters.
     * Independent from CxFindingsWindow to prevent cross-tab interference.
     * Thread-safe via synchronized set.
     */
    public static class IgnoredFindingsSeverityFilterState {
        private static final IgnoredFindingsSeverityFilterState INSTANCE = new IgnoredFindingsSeverityFilterState();
        private final Set<Filterable> selectedFilters = Collections.synchronizedSet(new HashSet<>());

        private IgnoredFindingsSeverityFilterState() { selectedFilters.addAll(Severity.DEFAULT_SEVERITIES); }

        public static IgnoredFindingsSeverityFilterState getInstance() { return INSTANCE; }

        /** Returns selected filters, restoring defaults if empty. */
        public Set<Filterable> getFilters() {
            if (selectedFilters.isEmpty()) selectedFilters.addAll(Severity.DEFAULT_SEVERITIES);
            return selectedFilters;
        }

        public boolean isSelected(Filterable filterable) { return getFilters().contains(filterable); }

        public void setSelected(Filterable filterable, boolean selected) {
            if (selected) selectedFilters.add(filterable);
            else selectedFilters.remove(filterable);
        }
    }

    // ========== Listener Interfaces ==========

    /** Listener for vulnerability type filter changes. */
    public interface TypeFilterChanged { void filterChanged(); }

    /** Listener for sort order changes. */
    public interface SortChanged { void sortChanged(); }

    /** Listener for severity filter changes. */
    public interface SeverityFilterChanged { void filterChanged(); }
}
