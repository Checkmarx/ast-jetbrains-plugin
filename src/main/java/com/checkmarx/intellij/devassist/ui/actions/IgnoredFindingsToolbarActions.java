package com.checkmarx.intellij.devassist.ui.actions;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Toolbar actions for the ignored findings view.
 * Contains filter dropdown and sort dropdown actions similar to scan results tab pattern.
 */
public class IgnoredFindingsToolbarActions {

    // =========================
    // FILTER DROPDOWN ACTION
    // =========================

    public static final Topic<TypeFilterChanged> TYPE_FILTER_TOPIC = Topic.create("Type Filter Changed", TypeFilterChanged.class);

    /**
     * Filter dropdown action that shows a popup menu with vulnerability type options.
     * Matches the structure shown in Filter_action.svg with "Vulnerability Type" header.
     */
    public static class VulnerabilityTypeFilterDropdown extends ActionGroup {

        public VulnerabilityTypeFilterDropdown() {
            super();
            getTemplatePresentation().setText("Filter");
            //getTemplatePresentation().setDescription("Filter");
            getTemplatePresentation().setIcon(CxIcons.Ignored.FILTER);
            setPopup(true); // This makes it show a dropdown menu like scan results tab
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                new VulnerabilityTypeHeader(), // "Vulnerability Type" header
                new SASTTypeFilter(),
                new SCATypeFilter(),
                new SecretsTypeFilter(),
                new ContainersTypeFilter(),
                new IaCTypeFilter()
            };
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Header action for "Vulnerability Type" section (non-clickable label with disabled styling)
     */
    public static class VulnerabilityTypeHeader extends AnAction {
        public VulnerabilityTypeHeader() {
            super("Vulnerability Type");
            getTemplatePresentation().setEnabled(false);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // Header - no action
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            // Apply disabled styling with theme-aware light gray color
            e.getPresentation().setEnabled(false);
            e.getPresentation().setText("Vulnerability Type");
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Base class for vulnerability type filter toggle actions (shown in dropdown).
     */
    public abstract static class VulnerabilityTypeFilter extends ToggleAction {
        protected final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
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
            messageBus.syncPublisher(TYPE_FILTER_TOPIC).filterChanged();
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    // Concrete vulnerability type filter implementations
    public static class SASTTypeFilter extends VulnerabilityTypeFilter {
        public SASTTypeFilter() {
            super("SAST", "Show/hide SAST vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SAST, ScanEngine.ASCA);
        }
    }

    public static class SCATypeFilter extends VulnerabilityTypeFilter {
        public SCATypeFilter() {
            super("SCA", "Show/hide SCA vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SCA, ScanEngine.OSS);
        }
    }

    public static class SecretsTypeFilter extends VulnerabilityTypeFilter {
        public SecretsTypeFilter() {
            super("Secrets", "Show/hide Secrets vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_SECRETS, ScanEngine.SECRETS);
        }
    }

    public static class ContainersTypeFilter extends VulnerabilityTypeFilter {
        public ContainersTypeFilter() {
            super("Containers", "Show/hide Containers vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_CONTAINERS, ScanEngine.CONTAINERS);
        }
    }

    public static class IaCTypeFilter extends VulnerabilityTypeFilter {
        public IaCTypeFilter() {
            super("IaC", "Show/hide IaC vulnerabilities", CxIcons.Ignored.ENGINE_CHIP_IAC, ScanEngine.IAC);
        }
    }

    // =========================
    // SORT DROPDOWN ACTION
    // =========================

    public static final Topic<SortChanged> SORT_TOPIC = Topic.create("Sort Changed", SortChanged.class);

    /**
     * Sort dropdown action that shows a popup menu with sorting options.
     * Matches the structure shown in Sort_action.svg with "Sort by" and "Order by" headers.
     */
    public static class SortDropdown extends ActionGroup {

        public SortDropdown() {
            super();
            getTemplatePresentation().setText("Sort");
            getTemplatePresentation().setDescription("Sort");
            getTemplatePresentation().setIcon(CxIcons.Ignored.SORT);
            setPopup(true);
        }

        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            return new AnAction[]{
                new SortByHeader(), // "Sort by" header
                new SortBySeverityHighToLow(),
                new SortBySeverityLowToHigh(),
                new SortByLastUpdated(),
                new SeparatorAction(), // Visual separator
                new OrderByHeader(), // "Order by" header
                new SortByLastUpdatedOldestFirst(),
                new SortByLastUpdatedNewestFirst()
            };
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Header action for "Sort By" section (non-clickable label with disabled styling)
     */
    public static class SortByHeader extends AnAction {
        public SortByHeader() {
            super("Sort By");
            getTemplatePresentation().setEnabled(false); // Make it non-clickable
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // Header - no action
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            // Apply disabled styling with theme-aware light gray color
            e.getPresentation().setEnabled(false);
            e.getPresentation().setText("Sort By");
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Header action for "Order By" section (non-clickable label with disabled styling)
     */
    public static class OrderByHeader extends AnAction {
        public OrderByHeader() {
            super("Order By");
            getTemplatePresentation().setEnabled(false); // Make it non-clickable
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // Header - no action
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            // Apply disabled styling with theme-aware light gray color
            e.getPresentation().setEnabled(false);
            e.getPresentation().setText("Order By");
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Separator action for visual separation between sections
     */
    public static class SeparatorAction extends AnAction {
        public SeparatorAction() {
            super("─────────"); // Visual separator line
            getTemplatePresentation().setEnabled(false);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // Separator - no action
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            super.update(e);
            // Apply disabled styling
            e.getPresentation().setEnabled(false);
            e.getPresentation().setText("─────────");
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    /**
     * Enum for different sort types matching Sort_action.svg structure
     */
    public enum SortType {
        SEVERITY_HIGH_TO_LOW("Severity: Critical > Low"),
        SEVERITY_LOW_TO_HIGH("Severity: Low > Critical"),
        LAST_UPDATED("Last Updated"),
        OLDEST_FIRST("Oldest First"),
        NEWEST_FIRST("Newest First");

        private final String displayName;

        SortType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Base class for sort toggle actions (shown in dropdown).
     */
    public abstract static class SortAction extends ToggleAction {
        protected final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
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
                messageBus.syncPublisher(SORT_TOPIC).sortChanged(sortType);
            }
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }
    }

    // Sort by severity actions
    public static class SortBySeverityHighToLow extends SortAction {
        public SortBySeverityHighToLow() {
            super(SortType.SEVERITY_HIGH_TO_LOW);
        }
    }

    public static class SortBySeverityLowToHigh extends SortAction {
        public SortBySeverityLowToHigh() {
            super(SortType.SEVERITY_LOW_TO_HIGH);
        }
    }

    // Sort by last updated action
    public static class SortByLastUpdated extends SortAction {
        public SortByLastUpdated() {
            super(SortType.LAST_UPDATED);
        }
    }

    // Order by actions
    public static class SortByLastUpdatedOldestFirst extends SortAction {
        public SortByLastUpdatedOldestFirst() {
            super(SortType.OLDEST_FIRST);
        }
    }

    public static class SortByLastUpdatedNewestFirst extends SortAction {
        public SortByLastUpdatedNewestFirst() {
            super(SortType.NEWEST_FIRST);
        }
    }

    // =========================
    // STATE MANAGERS
    // =========================

    /**
     * State manager for vulnerability type filters
     */
    public static class TypeFilterState {
        private static final TypeFilterState INSTANCE = new TypeFilterState();
        private final Set<ScanEngine> selectedEngines = Collections.synchronizedSet(EnumSet.allOf(ScanEngine.class));

        private TypeFilterState() {
            selectedEngines.remove(ScanEngine.ALL); // Remove the ALL enum as it's not a real engine
        }

        public static TypeFilterState getInstance() { return INSTANCE; }

        public boolean isSelected(ScanEngine engine) {
            return selectedEngines.contains(engine);
        }

        public void setSelected(ScanEngine engine, boolean selected) {
            if (selected) {
                selectedEngines.add(engine);
            } else {
                selectedEngines.remove(engine);
            }
        }

        public Set<ScanEngine> getSelectedEngines() {
            return new HashSet<>(selectedEngines);
        }

        public boolean hasActiveFilters() {
            Set<ScanEngine> allRealEngines = EnumSet.allOf(ScanEngine.class);
            allRealEngines.remove(ScanEngine.ALL);
            return !selectedEngines.containsAll(allRealEngines);
        }
    }

    /**
     * State manager for sort settings
     */
    public static class SortState {
        private static final SortState INSTANCE = new SortState();
        private SortType currentSort = SortType.SEVERITY_HIGH_TO_LOW;

        private SortState() {}

        public static SortState getInstance() { return INSTANCE; }

        public SortType getCurrentSort() { return currentSort; }

        public void setCurrentSort(SortType sortType) { this.currentSort = sortType; }
    }

    // =========================
    // INTERFACES
    // =========================

    public interface TypeFilterChanged {
        void filterChanged();
    }

    public interface SortChanged {
        void sortChanged(SortType sortType);
    }
}
