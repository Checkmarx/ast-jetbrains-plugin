package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.ui.actions.VulnerabilityFilterBaseAction;
import com.checkmarx.intellij.devassist.ui.actions.VulnerabilityFilterState;
import com.checkmarx.intellij.devassist.ui.actions.IgnoredFindingsToolbarActions;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool window panel that displays ignored security findings.
 * <p>
 * This panel provides a UI for viewing and managing vulnerabilities that have been
 * marked as ignored. It supports:
 * <ul>
 *   <li>Filtering by severity (Malicious, Critical, High, Medium, Low)</li>
 *   <li>Bulk selection via "Select All" checkbox</li>
 *   <li>Navigation to affected files</li>
 *   <li>Revive functionality to un-ignore findings</li>
 *   <li>Auto-refresh to stay in sync with ignore file changes</li>
 * </ul>
 * </p>
 *
 * @see IgnoreManager
 * @see IgnoreEntry
 */
public class CxIgnoredFindings extends SimpleToolWindowPanel implements Disposable {
    private static final Logger LOGGER = Utils.getLogger(CxIgnoredFindings.class);

    private final Project project;
    private final JPanel ignoredListPanel;
    private final Content content;
    private final IgnoreManager ignoreManager;
    private JCheckBox selectAllCheckbox;
    private final List<IgnoredEntryPanel> entryPanels = new java.util.ArrayList<>();
    private List<IgnoreEntry> allEntries = new java.util.ArrayList<>();

    // Track last known ignore file modification time for smart refresh
    private long lastKnownModificationTime = 0;

    /**
     * Creates a new ignored findings panel.
     *
     * @param project the current IntelliJ project
     * @param content the tool window content for updating the tab title
     */
    public CxIgnoredFindings(Project project, Content content) {
        super(false, true);
        this.project = project;
        this.content = content;
        this.ignoreManager = IgnoreManager.getInstance(project);
        this.ignoredListPanel = new JPanel();

        Runnable settingsCheckRunnable = () -> {
            if (new GlobalSettingsComponent().isValid()) {
                drawMainPanel();
            } else {
                drawAuthPanel();
            }
        };

        // Subscribe to filter changes
        project.getMessageBus().connect(this)
                .subscribe(VulnerabilityFilterBaseAction.TOPIC,
                        (VulnerabilityFilterBaseAction.VulnerabilityFilterChanged) () ->
                                ApplicationManager.getApplication().invokeLater(this::applyFiltersAndRefresh));

        // Subscribe to vulnerability type filter changes
        project.getMessageBus().connect(this)
                .subscribe(IgnoredFindingsToolbarActions.TYPE_FILTER_TOPIC,
                        (IgnoredFindingsToolbarActions.TypeFilterChanged) () ->
                                ApplicationManager.getApplication().invokeLater(this::applyFiltersAndRefresh));

        // Subscribe to sort changes
        project.getMessageBus().connect(this)
                .subscribe(IgnoredFindingsToolbarActions.SORT_TOPIC,
                        (IgnoredFindingsToolbarActions.SortChanged) sortType ->
                                ApplicationManager.getApplication().invokeLater(this::applyFiltersAndRefresh));

        // Subscribe to settings changes
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)
                .subscribe(SettingsListener.SETTINGS_APPLIED, (SettingsListener) settingsCheckRunnable::run);

        settingsCheckRunnable.run();

        // Subscribe to ignore file updates
        project.getMessageBus().connect(this)
                .subscribe(IgnoreFileManager.IGNORE_TOPIC,
                        (IgnoreFileManager.IgnoreListener) () ->
                                ApplicationManager.getApplication().invokeLater(this::refreshIgnoredEntries));

        // Add smart file change detection that preserves UI state
        // This detects changes in the ignore file without resetting user selections
        Timer smartRefreshTimer = new Timer(3000, e -> checkAndRefreshIfNeeded());
        smartRefreshTimer.start();

        // Add fallback VFS refresh for external edits (Notepad++, VS Code, etc.)
        // This ensures IntelliJ's virtual file system stays in sync with file system changes
        Timer vfsRefreshTimer = new Timer(10000, e -> {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path ignoreFilePath = Paths.get(basePath, ".idea", ".checkmarxIgnored");
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(ignoreFilePath.toFile());
                if (virtualFile != null) {
                    virtualFile.refresh(false, false);
                }
            }
        });
        vfsRefreshTimer.start();

        // Initialize file modification time tracking
        initializeFileModificationTime();

        // Dispose timers when component is disposed
        Disposer.register(this, smartRefreshTimer::stop);
        Disposer.register(this, vfsRefreshTimer::stop);
    }

    /**
     * Initializes the file modification time tracking for the ignore file.
     */
    private void initializeFileModificationTime() {
        try {
            String basePath = project.getBasePath();
            if (basePath != null) {
                Path ignoreFilePath = Paths.get(basePath, ".idea", ".checkmarxIgnored");
                if (java.nio.file.Files.exists(ignoreFilePath)) {
                    lastKnownModificationTime = java.nio.file.Files.getLastModifiedTime(ignoreFilePath).toMillis();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error initializing file modification time", e);
            lastKnownModificationTime = 0;
        }
    }

    /**
     * Checks if the ignore file has been modified and refreshes the UI only if needed.
     * This preserves user selections (checkboxes) while detecting actual file changes.
     * Works for both IntelliJ edits and external editor changes (like Notepad++).
     */
    private void checkAndRefreshIfNeeded() {
        if (!new GlobalSettingsComponent().isValid()) {
            return;
        }

        try {
            // Get the ignore file path
            String basePath = project.getBasePath();
            if (basePath == null) return;

            Path ignoreFilePath = Paths.get(basePath, ".idea", ".checkmarxIgnored");
            if (!java.nio.file.Files.exists(ignoreFilePath)) {
                // File doesn't exist - reset modification time and clear entries if we had any
                if (lastKnownModificationTime != 0 && !allEntries.isEmpty()) {
                    lastKnownModificationTime = 0;
                    refreshIgnoredEntries();
                }
                return;
            }

            // Check file modification time directly from file system (works for external edits)
            long currentModTime = java.nio.file.Files.getLastModifiedTime(ignoreFilePath).toMillis();

            // Only refresh if file was actually modified
            if (currentModTime != lastKnownModificationTime) {
                LOGGER.debug("Ignore file modified (timestamp changed from {} to {}), refreshing UI",
                    lastKnownModificationTime, currentModTime);
                lastKnownModificationTime = currentModTime;

                // For external edits, refresh IntelliJ's VFS to ensure it's in sync
                VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ignoreFilePath.toFile());
                if (virtualFile != null) {
                    // Refresh VFS entry to ensure IntelliJ knows about external changes
                    virtualFile.refresh(false, false);
                }

                // Small delay to ensure file system operations are complete
                ApplicationManager.getApplication().invokeLater(this::refreshIgnoredEntries);
            }
        } catch (Exception e) {
            LOGGER.warn("Error checking ignore file modification time", e);
        }
    }

    /**
     * Displays the authentication panel when global settings are not configured.
     * Shows the Checkmarx logo and a button to open settings.
     */
    private void drawAuthPanel() {
        removeAll();
        JPanel wrapper = new JPanel(new GridBagLayout());
        JPanel panel = new JPanel(new GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1));

        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        panel.add(new JBLabel(CxIcons.CHECKMARX_80), constraints);

        JButton openSettingsButton = new JButton(Bundle.message(Resource.OPEN_SETTINGS_BUTTON));
        openSettingsButton.addActionListener(e -> ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, GlobalSettingsConfigurable.class));

        constraints = new GridConstraints();
        constraints.setRow(1);
        panel.add(openSettingsButton, constraints);

        wrapper.add(panel);
        setContent(wrapper);
        updateTabTitle(0);
        revalidate();
        repaint();
    }

    /**
     * Draws the main panel with toolbar, header, and scrollable list of ignored findings.
     * Sets up auto-refresh timer to keep the display in sync with ignore file changes.
     */
    private void drawMainPanel() {
        removeAll();
        List<IgnoreEntry> entries = ignoreManager.getIgnoredEntries();

        if (entries.isEmpty()) {
            drawEmptyStatePanel();
        } else {
            ActionToolbar toolbar = createActionToolbar();
            toolbar.setTargetComponent(this);
            setToolbar(toolbar.getComponent());

            JPanel mainContainer = new JPanel(new BorderLayout());
            mainContainer.setBackground(JBUI.CurrentTheme.ToolWindow.background());
            mainContainer.add(createHeaderPanel(), BorderLayout.NORTH);

            ignoredListPanel.removeAll();
            ignoredListPanel.setLayout(new BoxLayout(ignoredListPanel, BoxLayout.Y_AXIS));
            ignoredListPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());

            JBScrollPane scrollPane = new JBScrollPane(ignoredListPanel);
            scrollPane.setBorder(JBUI.Borders.empty());
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            mainContainer.add(scrollPane, BorderLayout.CENTER);

            setContent(mainContainer);
            refreshIgnoredEntries();
        }

        // Note: Removed automatic 5-second refresh timer to prevent checkbox selections from being reset
        // File changes are already handled by message bus subscriptions (IgnoreFileManager.IGNORE_TOPIC)
        revalidate();
        repaint();
    }

    /**
     * Creates the action toolbar with severity filters, vulnerability type filter dropdown, and sort dropdown.
     * Similar to scan results tab pattern with popup dropdowns.
     *
     * @return the configured action toolbar
     */
    private ActionToolbar createActionToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Severity filters (existing - individual toggle actions)
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityMaliciousFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityCriticalFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityHighFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityMediumFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityLowFilter());

        // Add separator
        actionGroup.addSeparator();

        // Single vulnerability type filter dropdown (like scan results tab)
        actionGroup.add(new IgnoredFindingsToolbarActions.VulnerabilityTypeFilterDropdown());

        // Single sort dropdown (like scan results tab)
        actionGroup.add(new IgnoredFindingsToolbarActions.SortDropdown());

        return ActionManager.getInstance().createActionToolbar("CxIgnoredFindings", actionGroup, true);
    }

    /**
     * Displays an empty state panel when there are no ignored findings.
     */
    private void drawEmptyStatePanel() {
        setContent(createEmptyMessagePanel("No ignored findings"));
        setToolbar(null);
        updateTabTitle(0);
    }

    /**
     * Creates a centered message panel with the specified text.
     *
     * @param message the message to display
     * @return the configured panel
     */
    private JPanel createEmptyMessagePanel(String message) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(JBUI.CurrentTheme.ToolWindow.background());

        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        messagePanel.setBorder(JBUI.Borders.empty(40));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(JBUI.Fonts.label(14));
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        messagePanel.add(label, BorderLayout.CENTER);

        container.add(messagePanel, BorderLayout.CENTER);
        return container;
    }

    /**
     * Applies the current severity and vulnerability type filters, then sorts and refreshes the displayed entries.
     */
    private void applyFiltersAndRefresh() {
        if (!new GlobalSettingsComponent().isValid()) {
            return;
        }

        // Apply severity filters
        Set<Filterable> activeSeverityFilters = VulnerabilityFilterState.getInstance().getFilters();

        // Apply vulnerability type filters
        Set<ScanEngine> activeTypeFilters = IgnoredFindingsToolbarActions.TypeFilterState.getInstance().getSelectedEngines();
        boolean hasTypeFilters = IgnoredFindingsToolbarActions.TypeFilterState.getInstance().hasActiveFilters();

        List<IgnoreEntry> filteredEntries = allEntries.stream()
                .filter(entry -> shouldShowEntry(entry, activeSeverityFilters, activeTypeFilters, hasTypeFilters))
                .collect(Collectors.toList());

        // Apply sorting
        sortEntries(filteredEntries);

        displayFilteredEntries(filteredEntries);
    }

    /**
     * Determines if an entry should be shown based on active severity and vulnerability type filters.
     *
     * @param entry the ignore entry to check
     * @param activeSeverityFilters the set of active severity filters
     * @param activeTypeFilters the set of active vulnerability type filters
     * @param hasTypeFilters whether type filtering is active
     * @return true if the entry matches all active filters
     */
    private boolean shouldShowEntry(IgnoreEntry entry, Set<Filterable> activeSeverityFilters,
                                   Set<ScanEngine> activeTypeFilters, boolean hasTypeFilters) {
        // Check severity filter
        if (!activeSeverityFilters.isEmpty()) {
            boolean matchesSeverity = activeSeverityFilters.stream()
                    .anyMatch(filter -> filter.getFilterValue().equalsIgnoreCase(entry.severity));
            if (!matchesSeverity) {
                return false;
            }
        }

        // Check vulnerability type filter
        if (hasTypeFilters && entry.type != null) {
            return activeTypeFilters.contains(entry.type);
        }

        return true;
    }

    /**
     * Sorts the entries list based on the current sort setting.
     *
     * @param entries the list of entries to sort (modified in place)
     */
    private void sortEntries(List<IgnoreEntry> entries) {
        IgnoredFindingsToolbarActions.SortType currentSort = IgnoredFindingsToolbarActions.SortState.getInstance().getCurrentSort();

        switch (currentSort) {
            case SEVERITY_HIGH_TO_LOW:
                entries.sort((e1, e2) -> compareSeverity(e2.severity, e1.severity)); // Reverse for high to low
                break;
            case SEVERITY_LOW_TO_HIGH:
                entries.sort((e1, e2) -> compareSeverity(e1.severity, e2.severity));
                break;
            case LAST_UPDATED:
            case OLDEST_FIRST:
                entries.sort((e1, e2) -> compareLastUpdated(e1.dateAdded, e2.dateAdded));
                break;
            case NEWEST_FIRST:
                entries.sort((e1, e2) -> compareLastUpdated(e2.dateAdded, e1.dateAdded)); // Reverse for newest first
                break;
        }
    }

    /**
     * Compares severity levels for sorting.
     * Order: MALICIOUS > CRITICAL > HIGH > MEDIUM > LOW
     */
    private int compareSeverity(String severity1, String severity2) {
        if (severity1 == null && severity2 == null) return 0;
        if (severity1 == null) return 1; // Null goes last
        if (severity2 == null) return -1;

        int level1 = getSeverityLevel(severity1);
        int level2 = getSeverityLevel(severity2);

        return Integer.compare(level1, level2);
    }

    /**
     * Gets numeric level for severity (higher number = higher severity).
     */
    private int getSeverityLevel(String severity) {
        if (severity == null) return 0;
        switch (severity.toUpperCase()) {
            case "MALICIOUS": return 5;
            case "CRITICAL": return 4;
            case "HIGH": return 3;
            case "MEDIUM": return 2;
            case "LOW": return 1;
            default: return 0;
        }
    }

    /**
     * Compares last updated dates for sorting.
     */
    private int compareLastUpdated(String date1, String date2) {
        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return 1; // Null goes last
        if (date2 == null) return -1;

        try {
            java.time.ZonedDateTime dt1 = java.time.ZonedDateTime.parse(date1);
            java.time.ZonedDateTime dt2 = java.time.ZonedDateTime.parse(date2);
            return dt1.compareTo(dt2);
        } catch (Exception e) {
            // If parsing fails, fallback to string comparison
            return date1.compareTo(date2);
        }
    }

    /**
     * Displays the filtered list of ignored entries in the panel.
     *
     * @param entries the list of entries to display
     */
    private void displayFilteredEntries(List<IgnoreEntry> entries) {
        ignoredListPanel.removeAll();
        entryPanels.clear();

        if (entries.isEmpty()) {
            ignoredListPanel.add(createEmptyMessagePanel("No ignored vulnerabilities"));
        } else {
            for (IgnoreEntry entry : entries) {
                IgnoredEntryPanel panel = new IgnoredEntryPanel(entry);
                entryPanels.add(panel);
                ignoredListPanel.add(panel);
            }
        }

        updateSelectAllCheckbox();
        ignoredListPanel.revalidate();
        ignoredListPanel.repaint();
    }

    /**
     * Creates the header panel with column titles (Checkbox, Risk, Last Updated, Actions).
     * Uses flexible spacing between Risk, Last Updated, and Actions columns.
     *
     * @return the configured header panel
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerPanel.setBorder(JBUI.Borders.empty(12, 12, 0, 12));

        JPanel columnHeaderPanel = new JPanel();
        columnHeaderPanel.setLayout(new BoxLayout(columnHeaderPanel, BoxLayout.X_AXIS));
        columnHeaderPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        columnHeaderPanel.setBorder(JBUI.Borders.empty(12, 0, 8, 0));

        // Checkbox column
        columnHeaderPanel.add(createFixedColumnPanel(50, 30, createSelectAllCheckbox()));
        columnHeaderPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));

        // Risk column
        columnHeaderPanel.add(createRiskHeaderPanel());
        columnHeaderPanel.add(Box.createHorizontalGlue());

        // Last Updated column
        columnHeaderPanel.add(createColumnHeader("Last Updated", 140, 120, 160));
        columnHeaderPanel.add(Box.createHorizontalGlue());

        // Actions column (empty header)
        columnHeaderPanel.add(createFixedColumnPanel(140, 30, null));

        headerPanel.add(columnHeaderPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JCheckBox createSelectAllCheckbox() {
        selectAllCheckbox = new JCheckBox();
        selectAllCheckbox.setOpaque(false);
        selectAllCheckbox.addActionListener(e -> toggleSelectAll());
        return selectAllCheckbox;
    }

    private JPanel createFixedColumnPanel(int width, int height, Component component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        Dimension size = new Dimension(JBUI.scale(width), JBUI.scale(height));
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        panel.setMaximumSize(size);
        if (component != null) {
            panel.add(component);
        }
        return panel;
    }

    private JPanel createRiskHeaderPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(20), 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(30)));
        panel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(30)));
        panel.setMaximumSize(new Dimension(JBUI.scale(800), JBUI.scale(30)));

        JLabel label = new JLabel("Risk");
        label.setFont(JBUI.Fonts.label(12).asBold());
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        panel.add(label);
        return panel;
    }

    private JPanel createColumnHeader(String title, int preferred, int min, int max) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(JBUI.scale(preferred), JBUI.scale(30)));
        panel.setMinimumSize(new Dimension(JBUI.scale(min), JBUI.scale(30)));
        panel.setMaximumSize(new Dimension(JBUI.scale(max), JBUI.scale(30)));

        JLabel label = new JLabel(title);
        label.setFont(JBUI.Fonts.label(12).asBold());
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label);
        return panel;
    }

    /**
     * Refreshes the ignored entries from the ignore manager and updates the UI.
     * Handles transitions between empty and non-empty states.
     */
    private void refreshIgnoredEntries() {
        if (!new GlobalSettingsComponent().isValid()) {
            return;
        }

        try {
            List<IgnoreEntry> entries = ignoreManager.getIgnoredEntries();
            boolean wasEmpty = allEntries.isEmpty();
            boolean isNowEmpty = entries.isEmpty();

            allEntries = new java.util.ArrayList<>(entries);

            if (wasEmpty != isNowEmpty) {
                drawMainPanel();
            } else if (!isNowEmpty) {
                applyFiltersAndRefresh();
            }

            updateTabTitle(entries.size());
        } catch (Exception e) {
            LOGGER.warn("Error loading ignored entries", e);
            boolean hadEntries = !allEntries.isEmpty();
            allEntries.clear();

            if (hadEntries) {
                drawMainPanel();
            } else {
                displayFilteredEntries(List.of());
            }
            updateTabTitle(0);
        }
    }

    /**
     * Toggles selection state of all entry panels based on the select-all checkbox.
     */
    private void toggleSelectAll() {
        boolean selected = selectAllCheckbox.isSelected();
        entryPanels.forEach(panel -> panel.setSelected(selected));
    }

    /**
     * Updates the select-all checkbox state based on individual checkbox states.
     * Temporarily removes listeners to prevent recursive calls.
     */
    private void updateSelectAllCheckbox() {
        if (selectAllCheckbox == null || entryPanels.isEmpty()) {
            return;
        }

        boolean allSelected = entryPanels.stream().allMatch(IgnoredEntryPanel::isSelected);
        var listeners = selectAllCheckbox.getActionListeners();

        for (var listener : listeners) {
            selectAllCheckbox.removeActionListener(listener);
        }
        selectAllCheckbox.setSelected(allSelected);
        for (var listener : listeners) {
            selectAllCheckbox.addActionListener(listener);
        }
    }

    /**
     * Updates the tab title with the count of ignored findings.
     *
     * @param count the number of ignored findings
     */
    private void updateTabTitle(int count) {
        if (content != null) {
            String title = count > 0
                    ? DevAssistConstants.IGNORED_FINDINGS_TAB + " " + count
                    : DevAssistConstants.IGNORED_FINDINGS_TAB;
            content.setDisplayName(title);
        }
    }

    @Override
    public void dispose() {
        // Cleanup resources if needed
    }

    /**
     * Panel representing a single ignored entry in a row-like layout.
     * Displays vulnerability icon, severity, display name, engine chip, file buttons,
     * last updated date, and revive action button.
     */
    private class IgnoredEntryPanel extends JPanel {
        private final IgnoreEntry entry;
        private final JCheckBox selectCheckBox;

        /**
         * Creates a new panel for the given ignore entry.
         *
         * @param entry the ignore entry to display
         */
        public IgnoredEntryPanel(IgnoreEntry entry) {
            this.entry = entry;

            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(8, 12));
            setBackground(JBUI.CurrentTheme.ToolWindow.background());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(96)));

            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
            mainPanel.setOpaque(false);

            // Checkbox column
            selectCheckBox = new JCheckBox();
            selectCheckBox.setOpaque(false);
            selectCheckBox.addActionListener(e -> updateSelectAllCheckbox());
            mainPanel.add(createCheckboxColumn());
            mainPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));

            // Risk column
            mainPanel.add(createRiskColumn());
            mainPanel.add(Box.createHorizontalGlue());

            // Last Updated column
            mainPanel.add(createLastUpdatedColumn());
            mainPanel.add(Box.createHorizontalGlue());

            // Actions column
            mainPanel.add(createActionsColumn());

            add(mainPanel, BorderLayout.CENTER);
            setupHoverEffect();
        }

        private JPanel createCheckboxColumn() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(JBUI.scale(50), JBUI.scale(80)));
            panel.setMinimumSize(new Dimension(JBUI.scale(50), JBUI.scale(80)));
            panel.setMaximumSize(new Dimension(JBUI.scale(50), JBUI.scale(96)));

            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapper.setOpaque(false);
            wrapper.add(selectCheckBox);
            panel.add(wrapper, BorderLayout.CENTER);
            return panel;
        }

        private JPanel createRiskColumn() {
            JPanel riskPanel = buildRiskPanel();
            riskPanel.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(60)));
            riskPanel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(80)));
            riskPanel.setMaximumSize(new Dimension(JBUI.scale(800), Integer.MAX_VALUE));

            JPanel container = new JPanel(new BorderLayout());
            container.setOpaque(false);
            container.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(60)));
            container.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(80)));
            container.setMaximumSize(new Dimension(JBUI.scale(800), Integer.MAX_VALUE));
            container.add(riskPanel, BorderLayout.CENTER);
            return container;
        }

        private JPanel createLastUpdatedColumn() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(80)));
            panel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(80)));
            panel.setMaximumSize(new Dimension(JBUI.scale(160), Integer.MAX_VALUE));

            JLabel label = new JLabel(formatLastUpdated(entry.dateAdded));
            label.setFont(JBUI.Fonts.label(12));
            label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.TOP);
            panel.add(label, BorderLayout.CENTER);
            return panel;
        }

        private JPanel createActionsColumn() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(80)));
            panel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(80)));
            panel.setMaximumSize(new Dimension(JBUI.scale(160), Integer.MAX_VALUE));

            JButton reviveButton = new JButton(CxIcons.Ignored.REVIVE);
            reviveButton.setPreferredSize(new Dimension(JBUI.scale(90), JBUI.scale(28)));
            reviveButton.addActionListener(ev ->
                    LOGGER.info("Revive clicked for: " + (entry.packageName != null ? entry.packageName : "unknown")));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.FIRST_LINE_START;
            gbc.insets = JBUI.insets(10, 0, 0, 0);
            panel.add(reviveButton, gbc);
            return panel;
        }

        private void setupHoverEffect() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(JBUI.CurrentTheme.ToolWindow.background().brighter());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(JBUI.CurrentTheme.ToolWindow.background());
                }
            });
        }

        /**
         * Builds the risk panel containing vulnerability icon, severity, name, engine chip, and file buttons.
         */
        private JPanel buildRiskPanel() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Top line: vulnerability icon + severity icon + display name
            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(0)));
            topLine.setOpaque(false);
            topLine.add(new JLabel(getCardIcon(entry.type, entry.severity)));
            topLine.add(new JLabel(getSeverityIcon(entry.severity)));

            String displayName = formatDisplayName();
            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setFont(JBUI.Fonts.label(13).asBold());
            nameLabel.setToolTipText(displayName);
            nameLabel.setVerticalAlignment(SwingConstants.TOP);
            topLine.add(nameLabel);
            panel.add(topLine);

            // Bottom line: engine chip + file buttons
            JPanel filesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
            filesRow.setOpaque(false);
            filesRow.add(new JLabel(getEngineChipIcon(entry.type)));
            filesRow.add(buildFileButtons());
            panel.add(filesRow);

            return panel;
        }

        /**
         * Returns the appropriate severity icon for the given severity level.
         */
        private Icon getSeverityIcon(String severity) {
            if (severity == null) return CxIcons.Small.UNKNOWN;
            switch (severity.toLowerCase()) {
                case "critical": return CxIcons.Small.CRITICAL;
                case "high": return CxIcons.Small.HIGH;
                case "medium": return CxIcons.Small.MEDIUM;
                case "low": return CxIcons.Small.LOW;
                case "malicious": return CxIcons.Small.MALICIOUS;
                default: return CxIcons.Small.UNKNOWN;
            }
        }

        /**
         * Returns the appropriate card icon based on scan engine type and severity.
         */
        private Icon getCardIcon(ScanEngine type, String severity) {
            if (severity == null) severity = "unknown";
            String sev = severity.toLowerCase();

            switch (type) {
                case OSS:
                    return getPackageCardIcon(sev);
                case SECRETS:
                    return getSecretCardIcon(sev);
                case CONTAINERS:
                    return getContainersCardIcon(sev);
                default:
                    return getVulnerabilityCardIcon(sev);
            }
        }

        private Icon getPackageCardIcon(String severity) {
            switch (severity) {
                case "critical": return CxIcons.Ignored.CARD_PACKAGE_CRITICAL;
                case "high": return CxIcons.Ignored.CARD_PACKAGE_HIGH;
                case "medium": return CxIcons.Ignored.CARD_PACKAGE_MEDIUM;
                case "low": return CxIcons.Ignored.CARD_PACKAGE_LOW;
                case "malicious": return CxIcons.Ignored.CARD_PACKAGE_MALICIOUS;
                default: return CxIcons.Ignored.CARD_PACKAGE_MEDIUM;
            }
        }

        private Icon getSecretCardIcon(String severity) {
            switch (severity) {
                case "critical": return CxIcons.Ignored.CARD_SECRET_CRITICAL;
                case "high": return CxIcons.Ignored.CARD_SECRET_HIGH;
                case "medium": return CxIcons.Ignored.CARD_SECRET_MEDIUM;
                case "low": return CxIcons.Ignored.CARD_SECRET_LOW;
                case "malicious": return CxIcons.Ignored.CARD_SECRET_MALICIOUS;
                default: return CxIcons.Ignored.CARD_SECRET_MEDIUM;
            }
        }

        private Icon getContainersCardIcon(String severity) {
            switch (severity) {
                case "critical": return CxIcons.Ignored.CARD_CONTAINERS_CRITICAL;
                case "high": return CxIcons.Ignored.CARD_CONTAINERS_HIGH;
                case "medium": return CxIcons.Ignored.CARD_CONTAINERS_MEDIUM;
                case "low": return CxIcons.Ignored.CARD_CONTAINERS_LOW;
                case "malicious": return CxIcons.Ignored.CARD_CONTAINERS_MALICIOUS;
                default: return CxIcons.Ignored.CARD_CONTAINERS_MEDIUM;
            }
        }

        private Icon getVulnerabilityCardIcon(String severity) {
            switch (severity) {
                case "critical": return CxIcons.Ignored.CARD_VULNERABILITY_CRITICAL;
                case "high": return CxIcons.Ignored.CARD_VULNERABILITY_HIGH;
                case "medium": return CxIcons.Ignored.CARD_VULNERABILITY_MEDIUM;
                case "low": return CxIcons.Ignored.CARD_VULNERABILITY_LOW;
                case "malicious": return CxIcons.Ignored.CARD_VULNERABILITY_MALICIOUS;
                default: return CxIcons.Ignored.CARD_VULNERABILITY_MEDIUM;
            }
        }

        /**
         * Returns the engine chip icon for the given scan engine type.
         */
        private Icon getEngineChipIcon(ScanEngine type) {
            switch (type) {
                case OSS: return CxIcons.Ignored.ENGINE_CHIP_SCA;
                case SECRETS: return CxIcons.Ignored.ENGINE_CHIP_SECRETS;
                case IAC: return CxIcons.Ignored.ENGINE_CHIP_IAC;
                case ASCA: return CxIcons.Ignored.ENGINE_CHIP_SAST;
                case CONTAINERS: return CxIcons.Ignored.ENGINE_CHIP_CONTAINERS;
                default: return CxIcons.Ignored.ENGINE_CHIP_SCA;
            }
        }

        /**
         * Builds the file buttons panel showing affected files with expand functionality.
         */
        private JPanel buildFileButtons() {
            JPanel container = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(0)));
            container.setOpaque(false);

            if (entry.files == null || entry.files.isEmpty()) {
                JLabel none = new JLabel("No files");
                none.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
                container.add(none);
                return container;
            }

            List<IgnoreEntry.FileRef> activeFiles = entry.files.stream()
                    .filter(f -> f != null && f.active)
                    .collect(Collectors.toList());

            if (activeFiles.isEmpty()) {
                JLabel none = new JLabel("No files");
                none.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
                container.add(none);
                return container;
            }

            // Show first file, hide rest behind expand button
            container.add(createFileButton(activeFiles.get(0)));

            if (activeFiles.size() > 1) {
                List<IgnoreEntry.FileRef> hidden = activeFiles.subList(1, activeFiles.size());
                JButton expand = new JButton("and " + hidden.size() + " more files");
                expand.addActionListener(ev -> expandHiddenFiles(container, expand, hidden));
                container.add(expand);
            }

            return container;
        }

        private JButton createFileButton(IgnoreEntry.FileRef file) {
            String buttonText = formatFileLabel(file);
            String tooltipText = file.path + (file.line != null ? ":" + file.line : "");

            JButton btn = new JButton(buttonText);
            btn.setToolTipText(tooltipText);
            btn.addActionListener(ev -> navigateToFile(file));
            return btn;
        }

        private void expandHiddenFiles(JPanel container, JButton expandButton, List<IgnoreEntry.FileRef> hidden) {
            container.remove(expandButton);
            hidden.forEach(f -> container.add(createFileButton(f)));
            container.revalidate();
            container.repaint();

            // Propagate layout update to scroll pane
            Container parent = container;
            while (parent != null && !(parent instanceof JScrollPane)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }

        /**
         * Navigates to a specific file and line number in the IDE editor.
         *
         * Resolves workspace-relative paths and opens the file with proper line navigation.
         *
         * @param file File reference containing path and optional line number
         */
        private void navigateToFile(IgnoreEntry.FileRef file) {
            if (file == null || file.path == null) {
                return;
            }

            VirtualFile vFile = resolveVirtualFile(file.path);
            if (vFile == null) {
                showFileNotFoundNotification(file.path);
                return;
            }

            try {
                FileEditor[] editors = FileEditorManager.getInstance(project).openFile(vFile, true);

                if (editors.length == 0) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Could not open file in editor: " + file.path,
                        "File Navigation Error"
                    );
                    return;
                }

                // Navigate to specific line if available
                if (file.line != null && file.line > 0) {
                    FileEditor editor = editors[0];
                    if (editor instanceof TextEditor) {
                        Editor textEditor = ((TextEditor) editor).getEditor();

                        // Convert 1-based line to 0-based position
                        int zeroBasedLine = Math.max(0, file.line - 1);
                        LogicalPosition position = new LogicalPosition(zeroBasedLine, 0);
                        textEditor.getCaretModel().moveToLogicalPosition(position);
                        textEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                    }
                }

            } catch (Exception e) {
                LOGGER.warn("Error opening file: " + file.path, e);
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Error opening file: " + e.getMessage(),
                    "File Navigation Error"
                );
            }
        }

        /**
         * Shows a notification when a file cannot be found.
         */
        private void showFileNotFoundNotification(String filePath) {
            com.intellij.openapi.ui.Messages.showWarningDialog(
                project,
                "Could not open file: " + filePath + "\nThe file may have been moved or deleted.",
                "File Navigation Error"
            );
        }

        /**
         * Resolves a file path to a VirtualFile using workspace-relative path resolution.
         *
         * @param path The file path (relative to workspace root)
         * @return VirtualFile instance or null if not found
         */
        private VirtualFile resolveVirtualFile(String path) {
            if (path == null || path.isEmpty()) {
                return null;
            }

            // Basic path validation to prevent directory traversal
            if (path.contains("..")) {
                LOGGER.warn("Invalid path containing '..': " + path);
                return null;
            }

            String workspaceRoot = project.getBasePath();
            if (workspaceRoot == null) {
                return null;
            }

            // Check if path is absolute using IntelliJ utilities
            if (com.intellij.openapi.util.io.FileUtil.isAbsolute(path)) {
                return LocalFileSystem.getInstance().findFileByPath(path);
            }

            // Join workspace root with relative path
            String cleanPath = path.startsWith("./") ? path.substring(2) : path;
            cleanPath = cleanPath.replace("\\", "/");
            String absolutePath = workspaceRoot + "/" + cleanPath;

            return LocalFileSystem.getInstance().findFileByPath(absolutePath);
        }

        private String formatFileLabel(IgnoreEntry.FileRef f) {
            if (f == null || f.path == null) return "file";
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(f.path);
                String name = p.getFileName() != null ? p.getFileName().toString() : f.path;
                return f.line != null ? name + ":" + f.line : name;
            } catch (Exception ex) {
                return f.line != null ? f.path + ":" + f.line : f.path;
            }
        }

        /**
         * Formats the display name based on the scan engine type.
         */
        private String formatDisplayName() {
            String keyName = entry.packageName != null ? entry.packageName : "Unknown";
            switch (entry.type) {
                case OSS:
                    String mgr = entry.packageManager != null ? entry.packageManager : "pkg";
                    String ver = entry.packageVersion != null ? entry.packageVersion : "";
                    return mgr + "@" + keyName + (ver.isEmpty() ? "" : "@" + ver);
                case SECRETS:
                case IAC:
                    return keyName;
                case ASCA:
                    return entry.title != null ? entry.title : keyName;
                case CONTAINERS:
                    String tag = entry.imageTag != null ? entry.imageTag : entry.packageVersion;
                    return keyName + (tag != null && !tag.isEmpty() ? "@" + tag : "");
                default:
                    return keyName;
            }
        }

        /**
         * Formats an ISO date string into a human-readable relative time.
         */
        private String formatLastUpdated(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) return "Unknown";
            try {
                java.time.ZonedDateTime then = java.time.ZonedDateTime.parse(isoDate);
                java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
                long days = java.time.temporal.ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate());
                if (days == 0) return "Today";
                if (days == 1) return "1 day ago";
                if (days < 7) return days + " days ago";
                if (days < 30) return (days / 7) + " weeks ago";
                if (days < 365) return (days / 30) + " months ago";
                return (days / 365) + " years ago";
            } catch (Exception ex) {
                return isoDate;
            }
        }

        public boolean isSelected() {
            return selectCheckBox.isSelected();
        }

        public void setSelected(boolean selected) {
            selectCheckBox.setSelected(selected);
        }
    }

}
