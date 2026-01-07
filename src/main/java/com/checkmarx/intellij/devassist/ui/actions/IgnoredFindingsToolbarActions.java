package com.checkmarx.intellij.devassist.ui.actions;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Toolbar actions for the Ignored Findings tab.
 * Provides filter dropdown (vulnerability types), sort dropdown, and severity filters.
 * Uses independent state from CxFindingsWindow to avoid cross-tab interference.
 */
public class IgnoredFindingsToolbarActions {

    // ========== Message Topics ==========

    public static final Topic<TypeFilterChanged> TYPE_FILTER_TOPIC =
            Topic.create("Type Filter Changed", TypeFilterChanged.class);
    public static final Topic<SortChanged> SORT_TOPIC =
            Topic.create("Sort Changed", SortChanged.class);
    public static final Topic<SeverityFilterChanged> SEVERITY_FILTER_TOPIC =
            Topic.create("Ignored Findings Severity Filter Changed", SeverityFilterChanged.class);

    // ========== Filter Dropdown ==========

    /** Dropdown showing vulnerability type filter options (SAST, SCA, Secrets, etc.) */
    public static class VulnerabilityTypeFilterDropdown extends ActionGroup {
        public VulnerabilityTypeFilterDropdown() {
            getTemplatePresentation().setText("Filter");
            getTemplatePresentation().setIcon(CxIcons.Ignored.FILTER);
            setPopup(true);
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                new MenuHeader("Vulnerability Type"),
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
            getTemplatePresentation().setText("Sort");
            getTemplatePresentation().setDescription("Sort");
            getTemplatePresentation().setIcon(CxIcons.Ignored.SORT);
            setPopup(true);
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                new MenuHeader("Sort By"),
                new SortBySeverityHighToLow(),
                new SortBySeverityLowToHigh(),
                new SortByLastUpdated(),
                new MenuHeader("─────────"),
                new MenuHeader("Order By"),
                new SortByLastUpdatedOldestFirst(),
                new SortByLastUpdatedNewestFirst()
            };
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    // ========== Menu Helpers ==========

    /** Non-clickable header/separator for dropdown menus */
    public static class MenuHeader extends AnAction {
        public MenuHeader(String text) {
            super(text);
            getTemplatePresentation().setEnabled(false);
        }

        @Override public void actionPerformed(@NotNull AnActionEvent e) { }
        @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    // ========== Vulnerability Type Filters ==========

    /** Base class for vulnerability type filter toggles */
    public abstract static class VulnerabilityTypeFilter extends ToggleAction {
        protected final ScanEngine engineType;

        public VulnerabilityTypeFilter(String text, String description, Icon icon, ScanEngine engineType) {
            super(text, description, icon);
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
        public SASTTypeFilter() { super("SAST", "Show/hide SAST vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SAST, ScanEngine.ASCA); }
    }
    public static class SCATypeFilter extends VulnerabilityTypeFilter {
        public SCATypeFilter() { super("SCA", "Show/hide SCA vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SCA, ScanEngine.OSS); }
    }
    public static class SecretsTypeFilter extends VulnerabilityTypeFilter {
        public SecretsTypeFilter() { super("Secrets", "Show/hide Secrets vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SECRETS, ScanEngine.SECRETS); }
    }
    public static class ContainersTypeFilter extends VulnerabilityTypeFilter {
        public ContainersTypeFilter() { super("Containers", "Show/hide Containers vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_CONTAINERS, ScanEngine.CONTAINERS); }
    }
    public static class IaCTypeFilter extends VulnerabilityTypeFilter {
        public IaCTypeFilter() { super("IaC", "Show/hide IaC vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_IAC, ScanEngine.IAC); }
    }

    // ========== Sort Actions ==========

    public enum SortType {
        SEVERITY_HIGH_TO_LOW("Severity: Critical > Low"),
        SEVERITY_LOW_TO_HIGH("Severity: Low > Critical"),
        LAST_UPDATED("Last Updated"),
        OLDEST_FIRST("Oldest First"),
        NEWEST_FIRST("Newest First");

        private final String displayName;
        SortType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    /** Base class for sort toggle actions */
    public abstract static class SortAction extends ToggleAction {
        protected final SortType sortType;

        public SortAction(SortType sortType) {
            super(sortType.getDisplayName());
            this.sortType = sortType;
        }

        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
            return SortState.getInstance().getCurrentSort() == sortType;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
            if (state) {
                SortState.getInstance().setCurrentSort(sortType);
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(SORT_TOPIC).sortChanged(sortType);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    }

    public static class SortBySeverityHighToLow extends SortAction {
        public SortBySeverityHighToLow() { super(SortType.SEVERITY_HIGH_TO_LOW); }
    }
    public static class SortBySeverityLowToHigh extends SortAction {
        public SortBySeverityLowToHigh() { super(SortType.SEVERITY_LOW_TO_HIGH); }
    }
    public static class SortByLastUpdated extends SortAction {
        public SortByLastUpdated() { super(SortType.LAST_UPDATED); }
    }
    public static class SortByLastUpdatedOldestFirst extends SortAction {
        public SortByLastUpdatedOldestFirst() { super(SortType.OLDEST_FIRST); }
    }
    public static class SortByLastUpdatedNewestFirst extends SortAction {
        public SortByLastUpdatedNewestFirst() { super(SortType.NEWEST_FIRST); }
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

    // ========== State Managers ==========

    /** State manager for vulnerability type filters */
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

        public Set<ScanEngine> getSelectedEngines() { return new HashSet<>(selectedEngines); }

        public boolean hasActiveFilters() {
            Set<ScanEngine> allRealEngines = EnumSet.allOf(ScanEngine.class);
            allRealEngines.remove(ScanEngine.ALL);
            return !selectedEngines.containsAll(allRealEngines);
        }
    }

    /** State manager for sort settings */
    public static class SortState {
        private static final SortState INSTANCE = new SortState();
        private SortType currentSort = SortType.SEVERITY_HIGH_TO_LOW;

        private SortState() {}

        public static SortState getInstance() { return INSTANCE; }
        public SortType getCurrentSort() { return currentSort; }
        public void setCurrentSort(SortType sortType) { this.currentSort = sortType; }
    }

    /** State manager for severity filters - independent from CxFindingsWindow */
    public static class IgnoredFindingsSeverityFilterState {
        private static final IgnoredFindingsSeverityFilterState INSTANCE = new IgnoredFindingsSeverityFilterState();
        private final Set<Filterable> selectedFilters = Collections.synchronizedSet(new HashSet<>());

        private IgnoredFindingsSeverityFilterState() { selectedFilters.addAll(Severity.DEFAULT_SEVERITIES); }

        public static IgnoredFindingsSeverityFilterState getInstance() { return INSTANCE; }

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

    public interface TypeFilterChanged { void filterChanged(); }
    public interface SortChanged { void sortChanged(SortType sortType); }
    public interface SeverityFilterChanged { void filterChanged(); }
}
