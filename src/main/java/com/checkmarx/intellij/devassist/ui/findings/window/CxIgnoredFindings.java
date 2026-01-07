package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
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
import com.intellij.openapi.application.ModalityState;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tool window panel for viewing and managing ignored vulnerability findings.
 * Supports severity/type filtering, sorting, bulk selection, file navigation, and revive actions.
 * Toolbar actions are defined in {@link IgnoredFindingsToolbarActions}.
 *
 * @see IgnoreManager
 * @see IgnoredFindingsToolbarActions
 */
public class CxIgnoredFindings extends SimpleToolWindowPanel implements Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxIgnoredFindings.class);
    private static final String IGNORE_FILE_PATH = ".idea/.checkmarxIgnored";

    // ========== Instance Fields ==========
    private final Project project;
    private final Content content;
    private final JPanel ignoredListPanel = new JPanel();
    private final List<IgnoredEntryPanel> entryPanels = new ArrayList<>();

    private JCheckBox selectAllCheckbox;
    private JPanel headerPanel;
    private List<IgnoreEntry> allEntries = new ArrayList<>();
    private long lastKnownModificationTime = 0;

    /**
     * Creates a new ignored findings panel.
     *
     * @param project the current IntelliJ project
     * @param content the tool window content for tab title updates
     */
    public CxIgnoredFindings(Project project, Content content) {
        super(false, true);
        this.project = project;
        this.content = content;

        initializeSubscriptions();
        initializeTimers();
        initializeFileModificationTime();

        checkSettingsAndDraw();
    }

    private void initializeSubscriptions() {
        var projectBus = project.getMessageBus().connect(this);
        var appBus = ApplicationManager.getApplication().getMessageBus().connect(this);

        // Severity filter changes (independent state for this tab - see IgnoredFindingsToolbarActions)
        projectBus.subscribe(IgnoredFindingsToolbarActions.SEVERITY_FILTER_TOPIC,
                (IgnoredFindingsToolbarActions.SeverityFilterChanged) this::onFilterChanged);

        // Vulnerability type filter changes
        projectBus.subscribe(IgnoredFindingsToolbarActions.TYPE_FILTER_TOPIC,
                (IgnoredFindingsToolbarActions.TypeFilterChanged) this::onFilterChanged);

        // Sort changes
        projectBus.subscribe(IgnoredFindingsToolbarActions.SORT_TOPIC,
                (IgnoredFindingsToolbarActions.SortChanged) this::onFilterChanged);

        // Ignore file updates
        projectBus.subscribe(IgnoreFileManager.IGNORE_TOPIC,
                (IgnoreFileManager.IgnoreListener) () ->
                        ApplicationManager.getApplication().invokeLater(this::refreshIgnoredEntries));

        // Settings changes
        appBus.subscribe(SettingsListener.SETTINGS_APPLIED, (SettingsListener) this::checkSettingsAndDraw);
    }

    private void initializeTimers() {
        // Smart refresh: detects file changes without resetting user selections
        Timer smartRefreshTimer = new Timer(3000, e -> checkAndRefreshIfNeeded());
        smartRefreshTimer.start();

        // VFS refresh: syncs IntelliJ's VFS with external edits
        Timer vfsRefreshTimer = new Timer(10000, e -> refreshVirtualFileSystem());
        vfsRefreshTimer.start();

        Disposer.register(this, smartRefreshTimer::stop);
        Disposer.register(this, vfsRefreshTimer::stop);
    }

    private void initializeFileModificationTime() {
        try {
            Path ignoreFilePath = getIgnoreFilePath();
            if (ignoreFilePath != null && Files.exists(ignoreFilePath)) {
                lastKnownModificationTime = Files.getLastModifiedTime(ignoreFilePath).toMillis();
            }
        } catch (Exception e) {
            LOGGER.warn("Error initializing file modification time", e);
        }
    }

    private void onFilterChanged() {
        ApplicationManager.getApplication().invokeLater(this::applyFiltersAndRefresh);
    }

    private void checkSettingsAndDraw() {
        if (new GlobalSettingsComponent().isValid()) {
            drawMainPanel();
        } else {
            drawAuthPanel();
        }
    }

    private Path getIgnoreFilePath() {
        String basePath = project.getBasePath();
        return basePath != null ? Paths.get(basePath, IGNORE_FILE_PATH) : null;
    }

    private void refreshVirtualFileSystem() {
        Path ignoreFilePath = getIgnoreFilePath();
        if (ignoreFilePath != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(ignoreFilePath.toFile());
                if (vf != null) vf.refresh(false, false);
            }, ModalityState.NON_MODAL);
        }
    }

    /** Checks if an ignored file was modified externally and refreshes the UI while preserving selections. */
    private void checkAndRefreshIfNeeded() {
        if (!new GlobalSettingsComponent().isValid()) return;

        try {
            Path ignoreFilePath = getIgnoreFilePath();
            if (ignoreFilePath == null) return;

            if (!Files.exists(ignoreFilePath)) {
                if (lastKnownModificationTime != 0 && !allEntries.isEmpty()) {
                    lastKnownModificationTime = 0;
                    ApplicationManager.getApplication().invokeLater(this::refreshIgnoredEntries);
                }
                return;
            }

            long currentModTime = Files.getLastModifiedTime(ignoreFilePath).toMillis();
            if (currentModTime != lastKnownModificationTime) {
                LOGGER.debug("Ignore file modified, refreshing UI");
                lastKnownModificationTime = currentModTime;

                ApplicationManager.getApplication().invokeLater(() -> {
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ignoreFilePath.toFile());
                    if (vf != null) vf.refresh(false, false);
                    refreshIgnoredEntries();
                }, ModalityState.NON_MODAL);
            }
        } catch (Exception e) {
            LOGGER.warn("Error checking ignore file modification time", e);
        }
    }

    /** Displays the authentication panel when settings are not configured. */

    private void drawAuthPanel() {
        removeAll();

        JPanel panel = new JPanel(new GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1));
        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        panel.add(new JBLabel(CxIcons.CHECKMARX_80), constraints);

        JButton openSettingsButton = new JButton(Bundle.message(Resource.OPEN_SETTINGS_BUTTON));
        openSettingsButton.addActionListener(e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GlobalSettingsConfigurable.class));
        constraints = new GridConstraints();
        constraints.setRow(1);
        panel.add(openSettingsButton, constraints);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.add(panel);
        setContent(wrapper);
        updateTabTitle(0);
        revalidate();
        repaint();
    }

    /** Draws the main panel with toolbar, header, and scrollable findings list. */

    private void drawMainPanel() {
        removeAll();
        List<IgnoreEntry> entries = new IgnoreManager(project).getIgnoredEntries();

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
        revalidate();
        repaint();
    }

    private void drawEmptyStatePanel() {
        setContent(createEmptyMessagePanel(Bundle.message(Resource.IGNORED_NO_FINDINGS)));
        setToolbar(null);
        updateTabTitle(0);
    }

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

    /** Creates a toolbar with severity filters, type filter dropdown, and sort dropdown. */

    private ActionToolbar createActionToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Severity filters (uses IgnoredFindingsSeverityFilterState - independent from CxFindingsWindow)
        actionGroup.add(new IgnoredFindingsToolbarActions.IgnoredMaliciousFilter());
        actionGroup.add(new IgnoredFindingsToolbarActions.IgnoredCriticalFilter());
        actionGroup.add(new IgnoredFindingsToolbarActions.IgnoredHighFilter());
        actionGroup.add(new IgnoredFindingsToolbarActions.IgnoredMediumFilter());
        actionGroup.add(new IgnoredFindingsToolbarActions.IgnoredLowFilter());
        actionGroup.addSeparator();

        // Dropdowns (defined in IgnoredFindingsToolbarActions for filter and sort)
        actionGroup.add(new IgnoredFindingsToolbarActions.VulnerabilityTypeFilterDropdown());
        actionGroup.add(new IgnoredFindingsToolbarActions.SortDropdown());

        return ActionManager.getInstance().createActionToolbar("CxIgnoredFindings", actionGroup, true);
    }


    /** Applies current filters and sort, then refreshes the display. */
    private void applyFiltersAndRefresh() {
        if (!new GlobalSettingsComponent().isValid()) return;

        Set<Filterable> severityFilters = IgnoredFindingsToolbarActions.IgnoredFindingsSeverityFilterState.getInstance().getFilters();
        Set<ScanEngine> typeFilters = IgnoredFindingsToolbarActions.TypeFilterState.getInstance().getSelectedEngines();
        boolean hasTypeFilters = IgnoredFindingsToolbarActions.TypeFilterState.getInstance().hasActiveFilters();

        List<IgnoreEntry> filtered = allEntries.stream()
                .filter(e -> matchesSeverityFilter(e, severityFilters))
                .filter(e -> matchesTypeFilter(e, typeFilters, hasTypeFilters))
                .collect(Collectors.toList());

        sortEntries(filtered);
        displayFilteredEntries(filtered);
        updateTabTitle(filtered.size());
    }

    private boolean matchesSeverityFilter(IgnoreEntry entry, Set<Filterable> filters) {
        if (filters.isEmpty()) return true;
        return filters.stream().anyMatch(f -> f.getFilterValue().equalsIgnoreCase(entry.severity));
    }

    private boolean matchesTypeFilter(IgnoreEntry entry, Set<ScanEngine> filters, boolean hasFilters) {
        if (!hasFilters || entry.type == null) return true;
        return filters.contains(entry.type);
    }

    private void sortEntries(List<IgnoreEntry> entries) {
        IgnoredFindingsToolbarActions.SortState state = IgnoredFindingsToolbarActions.SortState.getInstance();
        IgnoredFindingsToolbarActions.SortField sortField = state.getSortField();
        IgnoredFindingsToolbarActions.DateOrder dateOrder = state.getDateOrder();

        switch (sortField) {
            case SEVERITY_HIGH_TO_LOW:
                entries.sort((a, b) -> Integer.compare(getSeverityLevel(b.severity), getSeverityLevel(a.severity)));
                break;
            case SEVERITY_LOW_TO_HIGH:
                entries.sort((a, b) -> Integer.compare(getSeverityLevel(a.severity), getSeverityLevel(b.severity)));
                break;
            case LAST_UPDATED:
                // Sort by date based on the selected date order
                if (dateOrder == IgnoredFindingsToolbarActions.DateOrder.OLDEST_FIRST) {
                    entries.sort((a, b) -> compareDates(a.dateAdded, b.dateAdded));
                } else {
                    entries.sort((a, b) -> compareDates(b.dateAdded, a.dateAdded));
                }
                break;
        }
    }

    /** Returns severity level (5=MALICIOUS, 4=CRITICAL, 3=HIGH, 2=MEDIUM, 1=LOW, 0=unknown). */
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

    private int compareDates(String date1, String date2) {
        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return 1;
        if (date2 == null) return -1;
        try {
            return ZonedDateTime.parse(date1).compareTo(ZonedDateTime.parse(date2));
        } catch (Exception e) {
            return date1.compareTo(date2);
        }
    }

    private void displayFilteredEntries(List<IgnoreEntry> entries) {
        ignoredListPanel.removeAll();
        entryPanels.clear();
        if (headerPanel != null) {
            headerPanel.setVisible(!entries.isEmpty());
        }
        if (entries.isEmpty()) {
            ignoredListPanel.add(createEmptyMessagePanel(Bundle.message(Resource.IGNORED_NO_FINDINGS)));
        } else {
            entries.forEach(entry -> {
                IgnoredEntryPanel panel = new IgnoredEntryPanel(entry);
                entryPanels.add(panel);
                ignoredListPanel.add(panel);
            });
        }
        updateSelectAllCheckbox();
        ignoredListPanel.revalidate();
        ignoredListPanel.repaint();
    }

    /** Creates a header with column titles: Checkbox, Risk, Last Updated, Actions. */
    private JPanel createHeaderPanel() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerPanel.setBorder(JBUI.Borders.empty(12, 12, 0, 12));

        JPanel columns = new JPanel();
        columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
        columns.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        columns.setBorder(JBUI.Borders.empty(12, 0, 8, 0));

        // Checkbox | Risk | Last Updated | Actions
        columns.add(createFixedColumn(50, createSelectAllCheckbox()));
        columns.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));
        columns.add(createFlexibleColumn(Bundle.message(Resource.IGNORED_RISK_COLUMN), 400, 500, 800, FlowLayout.LEFT));
        columns.add(Box.createHorizontalGlue());
        columns.add(createFlexibleColumn(Bundle.message(Resource.IGNORED_LAST_UPDATED_COLUMN), 120, 140, 160, FlowLayout.CENTER));
        columns.add(Box.createHorizontalGlue());
        columns.add(createFixedColumn(140, null));

        headerPanel.add(columns, BorderLayout.CENTER);
        return headerPanel;
    }

    private JCheckBox createSelectAllCheckbox() {
        selectAllCheckbox = new JCheckBox();
        selectAllCheckbox.setOpaque(false);
        selectAllCheckbox.addActionListener(e -> toggleSelectAll());
        return selectAllCheckbox;
    }

    private static final int HEADER_ROW_HEIGHT = 30;

    private JPanel createFixedColumn(int width, Component component) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel.setOpaque(false);
        Dimension size = new Dimension(JBUI.scale(width), JBUI.scale(HEADER_ROW_HEIGHT));
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        panel.setMaximumSize(size);
        if (component != null) panel.add(component);
        return panel;
    }

    @SuppressWarnings("MagicConstant")
    private JPanel createFlexibleColumn(String title, int min, int pref, int max, int alignment) {
        JPanel panel = new JPanel(new FlowLayout(alignment, alignment == FlowLayout.LEFT ? JBUI.scale(20) : 0, 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(JBUI.scale(min), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setPreferredSize(new Dimension(JBUI.scale(pref), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setMaximumSize(new Dimension(JBUI.scale(max), JBUI.scale(HEADER_ROW_HEIGHT)));

        JLabel label = new JLabel(title);
        label.setFont(JBUI.Fonts.label(12).asBold());
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        panel.add(label);
        return panel;
    }

    /** Refreshes entries from IgnoreManager and handles empty/non-empty state transitions. */
    private void refreshIgnoredEntries() {
        if (!new GlobalSettingsComponent().isValid()) return;

        try {
            List<IgnoreEntry> entries = new IgnoreManager(project).getIgnoredEntries();
            boolean wasEmpty = allEntries.isEmpty();
            boolean isNowEmpty = entries.isEmpty();

            allEntries = new ArrayList<>(entries);

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
            if (hadEntries) drawMainPanel();
            else displayFilteredEntries(List.of());
            updateTabTitle(0);
        }
    }

    private void toggleSelectAll() {
        boolean selected = selectAllCheckbox.isSelected();
        entryPanels.forEach(panel -> panel.setSelected(selected));
    }

    /** Updates select-all checkbox based on individual selections (removes listeners to prevent recursion). */
    private void updateSelectAllCheckbox() {
        if (selectAllCheckbox == null || entryPanels.isEmpty()) return;

        boolean allSelected = entryPanels.stream().allMatch(IgnoredEntryPanel::isSelected);
        var listeners = selectAllCheckbox.getActionListeners();

        for (var l : listeners) selectAllCheckbox.removeActionListener(l);
        selectAllCheckbox.setSelected(allSelected);
        for (var l : listeners) selectAllCheckbox.addActionListener(l);
    }

    private void updateTabTitle(int count) {
        if (content != null) {
            content.setDisplayName(count > 0
                    ? DevAssistConstants.IGNORED_FINDINGS_TAB + " " + count
                    : DevAssistConstants.IGNORED_FINDINGS_TAB);
        }
    }

    @Override
    public void dispose() {
        // Resources cleaned up via Disposer registrations
    }

    /**
     * Panel displaying a single ignored entry with checkbox, risk info, date, and actions.
     */
    private class IgnoredEntryPanel extends JPanel {
        private final IgnoreEntry entry;
        private final JCheckBox selectCheckBox;

        IgnoredEntryPanel(IgnoreEntry entry) {
            this.entry = entry;
            this.selectCheckBox = new JCheckBox();
            selectCheckBox.setOpaque(false);
            selectCheckBox.addActionListener(e -> updateSelectAllCheckbox());

            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(8, 12));
            setBackground(JBUI.CurrentTheme.ToolWindow.background());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(96)));

            add(buildRowPanel(), BorderLayout.CENTER);
            setupHoverEffect();
        }

        // ---------- Row Layout ----------

        private JPanel buildRowPanel() {
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);

            row.add(buildCheckboxColumn());
            row.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));
            row.add(buildRiskColumn());
            row.add(Box.createHorizontalGlue());
            row.add(buildLastUpdatedColumn());
            row.add(Box.createHorizontalGlue());
            row.add(buildActionsColumn());

            return row;
        }

        private JPanel buildCheckboxColumn() {
            JPanel panel = createCheckboxColumnPanel();
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapper.setOpaque(false);
            wrapper.add(selectCheckBox);
            panel.add(wrapper, BorderLayout.CENTER);
            return panel;
        }

        private JPanel buildRiskColumn() {
            JPanel riskPanel = buildRiskContent();
            setColumnSizes(riskPanel, 400, 500, 800, 60);

            JPanel container = new JPanel(new BorderLayout());
            container.setOpaque(false);
            setColumnSizes(container, 400, 500, 800, 60);
            container.add(riskPanel, BorderLayout.CENTER);
            return container;
        }

        private JPanel buildLastUpdatedColumn() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            setColumnSizes(panel, 120, 140, 160, ROW_HEIGHT);

            JLabel label = new JLabel(formatRelativeDate(entry.dateAdded));
            label.setFont(JBUI.Fonts.label(12));
            label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.TOP);
            panel.add(label, BorderLayout.CENTER);
            return panel;
        }

        private JPanel buildActionsColumn() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setOpaque(false);
            setColumnSizes(panel, 120, 140, 160, ROW_HEIGHT);

            JButton reviveButton = new JButton(CxIcons.Ignored.REVIVE);
            reviveButton.setPreferredSize(new Dimension(JBUI.scale(90), JBUI.scale(28)));
            reviveButton.addActionListener(ev ->
                    LOGGER.info("Revive clicked for: " + (entry.packageName != null ? entry.packageName : "unknown")));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.FIRST_LINE_START;
            gbc.insets = JBUI.insetsTop(10);
            panel.add(reviveButton, gbc);
            return panel;
        }

        // ---------- Risk Panel Content ----------

        private JPanel buildRiskContent() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Top: card icon + severity icon + name
            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            topLine.setOpaque(false);
            topLine.add(new JLabel(getCardIcon()));
            topLine.add(new JLabel(getSeverityIcon()));

            String name = formatDisplayName();
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(JBUI.Fonts.label(13).asBold());
            nameLabel.setToolTipText(name);
            topLine.add(nameLabel);
            panel.add(topLine);

            // Bottom: engine chip and file buttons
            JPanel bottomLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
            bottomLine.setOpaque(false);
            bottomLine.add(new JLabel(getEngineChipIcon()));
            bottomLine.add(buildFileButtons());
            panel.add(bottomLine);

            return panel;
        }

        // ---------- Icon Helpers ----------

        private Icon getSeverityIcon() {
            if (entry.severity == null) return CxIcons.Small.UNKNOWN;
            switch (entry.severity.toLowerCase()) {
                case "critical": return CxIcons.Small.CRITICAL;
                case "high": return CxIcons.Small.HIGH;
                case "medium": return CxIcons.Small.MEDIUM;
                case "low": return CxIcons.Small.LOW;
                case "malicious": return CxIcons.Small.MALICIOUS;
                default: return CxIcons.Small.UNKNOWN;
            }
        }

        private Icon getCardIcon() {
            String sev = entry.severity != null ? entry.severity.toLowerCase() : "medium";
            switch (entry.type) {
                case OSS: return getCardIconBySeverity(CxIcons.Ignored.CARD_PACKAGE_CRITICAL, CxIcons.Ignored.CARD_PACKAGE_HIGH,
                        CxIcons.Ignored.CARD_PACKAGE_MEDIUM, CxIcons.Ignored.CARD_PACKAGE_LOW, CxIcons.Ignored.CARD_PACKAGE_MALICIOUS, sev);
                case SECRETS: return getCardIconBySeverity(CxIcons.Ignored.CARD_SECRET_CRITICAL, CxIcons.Ignored.CARD_SECRET_HIGH,
                        CxIcons.Ignored.CARD_SECRET_MEDIUM, CxIcons.Ignored.CARD_SECRET_LOW, CxIcons.Ignored.CARD_SECRET_MALICIOUS, sev);
                case CONTAINERS: return getCardIconBySeverity(CxIcons.Ignored.CARD_CONTAINERS_CRITICAL, CxIcons.Ignored.CARD_CONTAINERS_HIGH,
                        CxIcons.Ignored.CARD_CONTAINERS_MEDIUM, CxIcons.Ignored.CARD_CONTAINERS_LOW, CxIcons.Ignored.CARD_CONTAINERS_MALICIOUS, sev);
                default: return getCardIconBySeverity(CxIcons.Ignored.CARD_VULNERABILITY_CRITICAL, CxIcons.Ignored.CARD_VULNERABILITY_HIGH,
                        CxIcons.Ignored.CARD_VULNERABILITY_MEDIUM, CxIcons.Ignored.CARD_VULNERABILITY_LOW, CxIcons.Ignored.CARD_VULNERABILITY_MALICIOUS, sev);
            }
        }

        private Icon getCardIconBySeverity(Icon critical, Icon high, Icon medium, Icon low, Icon malicious, String sev) {
            switch (sev) {
                case "critical": return critical;
                case "high": return high;
                case "low": return low;
                case "malicious": return malicious;
                default: return medium;
            }
        }

        private Icon getEngineChipIcon() {
            switch (entry.type) {
                case SECRETS: return CxIcons.Ignored.ENGINE_CHIP_SECRETS;
                case IAC: return CxIcons.Ignored.ENGINE_CHIP_IAC;
                case ASCA: return CxIcons.Ignored.ENGINE_CHIP_SAST;
                case CONTAINERS: return CxIcons.Ignored.ENGINE_CHIP_CONTAINERS;
                case OSS:
                default: return CxIcons.Ignored.ENGINE_CHIP_SCA;
            }
        }

        // ---------- File Buttons ----------

        private JPanel buildFileButtons() {
            JPanel container = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
            container.setOpaque(false);

            List<IgnoreEntry.FileReference> activeFiles = entry.files != null
                    ? entry.files.stream().filter(f -> f != null && f.active).collect(Collectors.toList())
                    : List.of();

            if (activeFiles.isEmpty()) {
                JLabel none = new JLabel(Bundle.message(Resource.IGNORED_NO_FILES));
                none.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
                container.add(none);
                return container;
            }

            container.add(createFileButton(activeFiles.get(0)));

            if (activeFiles.size() > 1) {
                List<IgnoreEntry.FileReference> hidden = activeFiles.subList(1, activeFiles.size());
                JButton expand = new JButton(Bundle.message(Resource.IGNORED_MORE_FILES, hidden.size()));
                expand.addActionListener(ev -> {
                    container.remove(expand);
                    hidden.forEach(f -> container.add(createFileButton(f)));
                    propagateRevalidate(container);
                });
                container.add(expand);
            }
            return container;
        }

        private JButton createFileButton(IgnoreEntry.FileReference file) {
            String label = formatFileLabel(file);
            JButton btn = new JButton(label);
            btn.setToolTipText(file.path + (file.line != null ? ":" + file.line : ""));
            btn.addActionListener(ev -> navigateToFile(file));
            return btn;
        }

        private void propagateRevalidate(Container container) {
            Container parent = container;
            while (parent != null && !(parent instanceof JScrollPane)) parent = parent.getParent();
            if (parent != null) { parent.revalidate(); parent.repaint(); }
        }

        // ---------- File Navigation ----------

        private void navigateToFile(IgnoreEntry.FileReference file) {
            if (file == null || file.path == null) return;

            VirtualFile vFile = resolveVirtualFile(file.path);
            if (vFile == null) {
                com.intellij.openapi.ui.Messages.showWarningDialog(project,
                        Bundle.message(Resource.IGNORED_FILE_OPEN_ERROR, file.path),
                        Bundle.message(Resource.IGNORED_FILE_NAV_ERROR));
                return;
            }

            try {
                FileEditor[] editors = FileEditorManager.getInstance(project).openFile(vFile, true);
                if (editors.length == 0) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(project,
                            Bundle.message(Resource.IGNORED_FILE_OPEN_ERROR, file.path),
                            Bundle.message(Resource.IGNORED_FILE_NAV_ERROR));
                    return;
                }

                if (file.line != null && file.line > 0 && editors[0] instanceof TextEditor) {
                    Editor textEditor = ((TextEditor) editors[0]).getEditor();
                    LogicalPosition pos = new LogicalPosition(Math.max(0, file.line - 1), 0);
                    textEditor.getCaretModel().moveToLogicalPosition(pos);
                    textEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
                }
            } catch (Exception e) {
                LOGGER.warn("Error opening file: " + file.path, e);
                com.intellij.openapi.ui.Messages.showErrorDialog(project,
                        Bundle.message(Resource.IGNORED_FILE_ERROR, e.getMessage()),
                        Bundle.message(Resource.IGNORED_FILE_NAV_ERROR));
            }
        }

        private VirtualFile resolveVirtualFile(String path) {
            if (path == null || path.isEmpty() || path.contains("..")) return null;

            String workspaceRoot = project.getBasePath();
            if (workspaceRoot == null) return null;

            if (com.intellij.openapi.util.io.FileUtil.isAbsolute(path)) {
                return LocalFileSystem.getInstance().findFileByPath(path);
            }

            String cleanPath = path.startsWith("./") ? path.substring(2) : path;
            return LocalFileSystem.getInstance().findFileByPath(workspaceRoot + "/" + cleanPath.replace("\\", "/"));
        }

        // ---------- Formatting Helpers ----------

        private String formatFileLabel(IgnoreEntry.FileReference f) {
            if (f == null || f.path == null) return "file";
            try {
                String name = Paths.get(f.path).getFileName().toString();
                return f.line != null ? name + ":" + f.line : name;
            } catch (Exception ex) {
                return f.line != null ? f.path + ":" + f.line : f.path;
            }
        }

        private String formatDisplayName() {
            String key = entry.packageName != null ? entry.packageName : Bundle.message(Resource.IGNORED_UNKNOWN);
            switch (entry.type) {
                case OSS:
                    String mgr = entry.packageManager != null ? entry.packageManager : "pkg";
                    String ver = entry.packageVersion != null ? entry.packageVersion : "";
                    return mgr + "@" + key + (ver.isEmpty() ? "" : "@" + ver);
                case ASCA:
                    return entry.title != null ? entry.title : key;
                case CONTAINERS:
                    String tag = entry.imageTag != null ? entry.imageTag : entry.packageVersion;
                    return key + (tag != null && !tag.isEmpty() ? "@" + tag : "");
                case SECRETS:
                case IAC:
                default:
                    return key;
            }
        }

        private String formatRelativeDate(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) return Bundle.message(Resource.IGNORED_UNKNOWN);
            try {
                ZonedDateTime then = ZonedDateTime.parse(isoDate);
                long days = ChronoUnit.DAYS.between(then.toLocalDate(), ZonedDateTime.now().toLocalDate());
                if (days == 0) return Bundle.message(Resource.IGNORED_TODAY);
                if (days == 1) return "1 day ago";
                if (days < 7) return days + " days ago";
                if (days < 30) return (days / 7) + " weeks ago";
                if (days < 365) return (days / 30) + " months ago";
                return (days / 365) + " years ago";
            } catch (Exception ex) {
                return isoDate;
            }
        }

        // ---------- UI Helpers ----------

        private static final int CHECKBOX_COL_WIDTH = 50;
        private static final int ROW_HEIGHT = 80;
        private static final int ROW_MAX_HEIGHT = 96;

        /** Creates a panel for the checkbox column with fixed dimensions. */
        private JPanel createCheckboxColumnPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setPreferredSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), JBUI.scale(ROW_HEIGHT)));
            panel.setMinimumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), JBUI.scale(ROW_HEIGHT)));
            panel.setMaximumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), JBUI.scale(ROW_MAX_HEIGHT)));
            return panel;
        }

        /** Sets horizontal sizing with flexible height (min=minH, pref=ROW_HEIGHT, max=unlimited). */
        private void setColumnSizes(JPanel panel, int minW, int prefW, int maxW, int minH) {
            panel.setMinimumSize(new Dimension(JBUI.scale(minW), JBUI.scale(minH)));
            panel.setPreferredSize(new Dimension(JBUI.scale(prefW), JBUI.scale(ROW_HEIGHT)));
            panel.setMaximumSize(new Dimension(JBUI.scale(maxW), Integer.MAX_VALUE));
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

        public boolean isSelected() { return selectCheckBox.isSelected(); }
        public void setSelected(boolean selected) { selectCheckBox.setSelected(selected); }
    }
}
