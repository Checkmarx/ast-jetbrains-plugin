package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.CxIcons;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.ui.CommonPanels;
import com.checkmarx.intellij.common.ui.DevAssistPromotionalPanel;
import com.checkmarx.intellij.common.window.actions.filter.Filterable;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.ui.actions.IgnoredFindingsToolbarActions;
import com.checkmarx.intellij.devassist.ui.layout.WrapLayout;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
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
import java.util.Map;
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
public class DevAssistIgnoredFindings extends SimpleToolWindowPanel implements Disposable {

    private static final Logger LOGGER = Utils.getLogger(DevAssistIgnoredFindings.class);
    private static final String IGNORE_FILE_PATH = ".idea/.checkmarxIgnored";
    private static final String FONT_FAMILY_MENLO = "Menlo";
    private static final String FONT_FAMILY_INTER = "Inter";
    private static final String FONT_FAMILY_SF_PRO = "SF Pro";

    // ========== Theme Colors (Figma design specs) ==========
    // JBColor(lightColor, darkColor) - automatically switches based on current theme
    private static final JBColor TEXT_COLOR = new JBColor(0x52545F, 0xADADAD);      // Text in pills and links
    private static final JBColor LINK_COLOR = new JBColor(0x606572, 0xADADAD);      // Underlined links
    private static final JBColor PILL_BG = new JBColor(0xFFFFFF, 0x323438);         // Pill button background (light=white, dark=gray)
    private static final JBColor PILL_BORDER = new JBColor(0x9DA3B4, 0x43454A);     // Pill button border
    private static final JBColor DIVIDER_COLOR = new JBColor(0xADADAD, 0xADADAD);   // Vertical divider line

    // ========== Icon Lookup Maps ==========
    private static final Map<String, Icon> SEVERITY_ICONS = Map.of(
            "critical", CxIcons.Medium.CRITICAL, "high", CxIcons.Medium.HIGH,
            "medium", CxIcons.Medium.MEDIUM, "low", CxIcons.Medium.LOW, "malicious", CxIcons.Medium.MALICIOUS);

    private static final Map<ScanEngine, Icon> ENGINE_CHIP_ICONS = Map.of(
            ScanEngine.SECRETS, CxIcons.Ignored.ENGINE_CHIP_SECRETS, ScanEngine.IAC, CxIcons.Ignored.ENGINE_CHIP_IAC,
            ScanEngine.ASCA, CxIcons.Ignored.ENGINE_CHIP_SAST, ScanEngine.CONTAINERS, CxIcons.Ignored.ENGINE_CHIP_CONTAINERS,
            ScanEngine.OSS, CxIcons.Ignored.ENGINE_CHIP_SCA);

    // ========== Topic for publishing ignored findings count changes ==========
    public static final Topic<IgnoredCountListener> IGNORED_COUNT_TOPIC =
            Topic.create("Ignored Findings Count Changed", IgnoredCountListener.class);

    /**
     * Listener interface for ignored findings count changes.
     * Subscribers can use this to update UI elements that display the count.
     */
    public interface IgnoredCountListener {
        void onCountChanged(int count);
    }

    // ========== Instance Fields ==========
    private final Project project;
    private final Content content;
    private final JPanel ignoredListPanel = new JPanel();
    private final List<IgnoredEntryPanel> entryPanels = new ArrayList<>();

    private JCheckBox selectAllCheckbox;
    private JPanel headerPanel;
    private JPanel selectionBarPanel;
    private JLabel selectionCountLabel;
    private List<IgnoreEntry> allEntries = new ArrayList<>();
    private long lastKnownModificationTime = 0;

    /**
     * Creates a new ignored findings panel.
     *
     * @param project the current IntelliJ project
     * @param content the tool window content for tab title updates
     */
    public DevAssistIgnoredFindings(Project project, Content content) {
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
        try {
            if (!Utils.isAuthenticated()) {
                LOGGER.info("CxIgnoredFindings: Not authenticated - showing auth panel");
                drawAuthPanel();
            } else {
                // Authenticated - check licenses (cached in GlobalSettingsState during authentication)
                GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
                boolean hasOneAssist = settingsState.isOneAssistLicenseEnabled();
                boolean hasDevAssist = settingsState.isDevAssistLicenseEnabled();
                boolean hasAnyLicense = hasOneAssist || hasDevAssist;

                LOGGER.info("CxIgnoredFindings: Authenticated, hasOneAssist=" + hasOneAssist
                        + ", hasDevAssist=" + hasDevAssist + ", hasAnyLicense=" + hasAnyLicense);

                if (hasAnyLicense) {
                    drawMainPanel();
                } else {
                    // No license (platform-only) - delete ignore files and show promotional panel
                    IgnoreFileManager.getInstance(project).deleteIgnoreFiles();
                    drawPromotionalPanel();
                }
            }
        } catch (Exception e) {
            LOGGER.error("CxIgnoredFindings: Error during settings check, showing auth panel as fallback", e);
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

    /**
     * Checks if an ignored file was modified externally and refreshes the UI while preserving selections.
     */
    private void checkAndRefreshIfNeeded() {
        if (!Utils.isAuthenticated()) return;

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

    /**
     * Displays the authentication panel when settings are not configured.
     */
    private void drawAuthPanel() {
        removeAll();
        setContent(CommonPanels.createAuthPanel(project));
        updateTabTitle(0);
        revalidate();
        repaint();
    }

    /**
     * Draws the full-screen promotional panel for Dev Assist.
     * Shown when authenticated but neither One Assist nor Dev Assist license is active.
     * Shows the cube image with title, description, and contact message - same as CxFindingsWindow.
     * No toolbar, no count.
     */
    private void drawPromotionalPanel() {
        LOGGER.info("drawPromotionalPanel: Drawing full-screen promotional panel");
        removeAll();

        // Remove toolbar when showing promotional panel
        if (getToolbar() != null) {
            setToolbar(null);
        }

        // Use DevAssistPromotionalPanel - same as CxFindingsWindow
        DevAssistPromotionalPanel promotionalPanel = new DevAssistPromotionalPanel();
        JBScrollPane scrollPane = new JBScrollPane(promotionalPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        setContent(scrollPane);

        // Update tab title with no count (just the tab name)
        if (content != null) {
            content.setDisplayName(DevAssistConstants.IGNORED_FINDINGS_TAB);
        }

        revalidate();
        repaint();
        LOGGER.info("drawPromotionalPanel: Promotional panel set as content");
    }

    /**
     * Draws the main panel with toolbar, header, and scrollable findings list.
     */

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
        // Only set toolbar to null if it was previously set to avoid NPE during initialization
        if (getToolbar() != null) {
            setToolbar(null);
        }
        updateTabTitle(0);
    }

    private JPanel createEmptyMessagePanel(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        panel.setBorder(JBUI.Borders.empty(40));
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(JBUI.Fonts.label(14));
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a toolbar with severity filters, type filter dropdown, and sort dropdown.
     */

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


    /**
     * Applies current filters and sort, then refreshes the display.
     */
    private void applyFiltersAndRefresh() {
        if (!Utils.isAuthenticated()) return;

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

    // Severity level lookup: MALICIOUS=5, CRITICAL=4, HIGH=3, MEDIUM=2, LOW=1, unknown=0
    private static final Map<String, Integer> SEVERITY_LEVELS = Map.of(
            "MALICIOUS", 5, "CRITICAL", 4, "HIGH", 3, "MEDIUM", 2, "LOW", 1);

    private int getSeverityLevel(String severity) {
        return severity == null ? 0 : SEVERITY_LEVELS.getOrDefault(severity.toUpperCase(), 0);
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

    /**
     * Creates a header with selection bar and column titles: Checkbox, Risk, Last Updated, Actions.
     */
    private JPanel createHeaderPanel() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerPanel.setBorder(JBUI.Borders.empty(12, 12, 0, 12));

        // Create selection bar (hidden by default)
        selectionBarPanel = createSelectionBar();
        selectionBarPanel.setVisible(false);

        // Create columns panel: Checkbox | Risk (expands) | Last Updated | Actions
        JPanel columnsPanel = new JPanel();
        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.X_AXIS));
        columnsPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        columnsPanel.setBorder(JBUI.Borders.empty(12, 0, 8, 0));
        columnsPanel.add(createFixedColumn(50, createSelectAllCheckbox()));
        columnsPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));
        columnsPanel.add(createRiskColumnHeader());
        columnsPanel.add(createLastUpdatedColumnHeader());
        columnsPanel.add(Box.createHorizontalGlue());
        columnsPanel.add(createFixedColumn(140, null));

        // Container for selection bar and columns (stacked vertically)
        JPanel headerContent = new JPanel();
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        headerContent.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerContent.add(selectionBarPanel);
        headerContent.add(columnsPanel);

        headerPanel.add(headerContent, BorderLayout.CENTER);
        return headerPanel;
    }

    /**
     * Creates the selection bar (count label | divider | clear | revive buttons). Height=56px.
     */
    private JPanel createSelectionBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        bar.setBorder(JBUI.Borders.empty(8, 0));
        Dimension barSize = new Dimension(Integer.MAX_VALUE, JBUI.scale(56));
        bar.setPreferredSize(barSize);
        bar.setMinimumSize(new Dimension(0, JBUI.scale(56)));
        bar.setMaximumSize(barSize);

        selectionCountLabel = new JLabel("0 Risks selected");
        selectionCountLabel.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, 14));

        // Vertical divider (24x40)
        Dimension divSize = new Dimension(JBUI.scale(24), JBUI.scale(40));
        JPanel divider = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(DIVIDER_COLOR);
                g.drawLine(getWidth() / 2, 8, getWidth() / 2, getHeight() - 8);
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(divSize);
        divider.setMinimumSize(divSize);
        divider.setMaximumSize(divSize);

        // Clear and revive buttons
        JLabel clearBtn = createClickableLabel(CxIcons.Ignored.CLEAR_SELECTION, e -> clearSelection());
        JLabel reviveBtn = createClickableLabel(CxIcons.Ignored.REVIVE_SELECTED, e -> reviveSelectedEntries());
        Dimension reviveSize = new Dimension(JBUI.scale(180), JBUI.scale(40));
        JPanel revivePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        revivePanel.setOpaque(false);
        revivePanel.setPreferredSize(reviveSize);
        revivePanel.setMinimumSize(reviveSize);
        revivePanel.setMaximumSize(reviveSize);
        revivePanel.add(reviveBtn);

        // Assemble: count | divider | clear | glue | revive
        bar.add(selectionCountLabel);
        bar.add(Box.createRigidArea(new Dimension(JBUI.scale(16), 0)));
        bar.add(divider);
        bar.add(Box.createRigidArea(new Dimension(JBUI.scale(8), 0)));
        bar.add(clearBtn);
        bar.add(Box.createHorizontalGlue());
        bar.add(revivePanel);
        return bar;
    }

    /**
     * Creates a clickable label with hand cursor.
     */
    private JLabel createClickableLabel(Icon icon, java.util.function.Consumer<MouseEvent> onClick) {
        JLabel label = new JLabel(icon);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.accept(e);
            }
        });
        return label;
    }

    /**
     * Revives all selected entries.
     */
    private void reviveSelectedEntries() {
        List<IgnoreEntry> selectedEntries = entryPanels.stream()
                .filter(IgnoredEntryPanel::isSelected)
                .map(panel -> panel.entry)  // Extract IgnoreEntry from each panel
                .collect(Collectors.toList());

        LOGGER.info("Revive selected clicked for " + selectedEntries.size() + " entries");
        new IgnoreManager(project).reviveMultipleEntries(selectedEntries);
    }


    /**
     * Clears all selections.
     */
    private void clearSelection() {
        entryPanels.forEach(panel -> panel.setSelected(false));
        updateSelectionState();
    }

    /**
     * Updates the selection bar visibility and count based on current selections.
     */
    private void updateSelectionState() {
        long selectedCount = entryPanels.stream().filter(IgnoredEntryPanel::isSelected).count();
        boolean hasSelection = selectedCount > 0;

        if (selectionBarPanel != null) {
            selectionBarPanel.setVisible(hasSelection);
        }
        if (selectionCountLabel != null) {
            // Format: "N Risks selected" (or "1 Risk selected" for singular)
            String riskText = selectedCount == 1 ? "Risk" : "Risks";
            selectionCountLabel.setText(selectedCount + " " + riskText + " selected");
        }

        updateSelectAllCheckbox();

        if (headerPanel != null) {
            headerPanel.revalidate();
            headerPanel.repaint();
        }
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

    /**
     * Creates Risk column header (left-aligned, expands).
     */
    private JPanel createRiskColumnHeader() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(20), 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(HEADER_ROW_HEIGHT)));
        JLabel label = new JLabel(Bundle.message(Resource.IGNORED_RISK_COLUMN));
        label.setFont(new Font(FONT_FAMILY_INTER, Font.PLAIN, 14));
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        panel.add(label);
        return panel;
    }

    /**
     * Creates Last Updated column header (center-aligned, fixed 120-160px).
     */
    private JPanel createLastUpdatedColumnHeader() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setMaximumSize(new Dimension(JBUI.scale(160), JBUI.scale(HEADER_ROW_HEIGHT)));
        JLabel label = new JLabel("<html><nobr>" + Bundle.message(Resource.IGNORED_LAST_UPDATED_COLUMN) + "</nobr></html>");
        label.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, 14));
        label.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        panel.add(label);
        return panel;
    }

    /**
     * Refreshes entries from IgnoreManager and handles empty/non-empty state transitions.
     */
    private void refreshIgnoredEntries() {
        if (!Utils.isAuthenticated()) return;

        try {
            List<IgnoreEntry> entries = new IgnoreManager(project).getIgnoredEntries();
            boolean wasEmpty = allEntries.isEmpty();
            boolean isNowEmpty = entries.isEmpty();

            allEntries = new ArrayList<>(entries);

            if (wasEmpty != isNowEmpty) {
                checkSettingsAndDraw();
            } else if (!isNowEmpty) {
                applyFiltersAndRefresh();
                updateTabTitle(entries.size());
            }
        } catch (Exception e) {
            LOGGER.warn("Error loading ignored entries", e);
            boolean hadEntries = !allEntries.isEmpty();
            allEntries.clear();
            if (hadEntries) {
                // Re-check settings to show proper UI based on license state
                checkSettingsAndDraw();
            } else {
                displayFilteredEntries(List.of());
                updateTabTitle(0);
            }
        }
    }

    private void toggleSelectAll() {
        boolean selected = selectAllCheckbox.isSelected();
        entryPanels.forEach(panel -> panel.setSelected(selected));
        updateSelectionState();
    }

    /**
     * Updates select-all checkbox based on individual selections (removes listeners to prevent recursion).
     */
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
        // Publish the count to subscribers (e.g., CxFindingsWindow promotional panel)
        project.getMessageBus().syncPublisher(IGNORED_COUNT_TOPIC).onCountChanged(count);
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
            selectCheckBox.addActionListener(e -> updateSelectionState());

            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(8, 12));
            setBackground(JBUI.CurrentTheme.ToolWindow.background());
            // Allow row to expand based on content - no max height constraint
            // Content will be: title (50px) + description (max 2 lines ~36px) + bottom line (expands with file buttons)
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            add(buildRowPanel(), BorderLayout.CENTER);  // Use CENTER to allow vertical expansion
            setupHoverEffect();
        }

        // ---------- Row Layout ----------

        private JPanel buildRowPanel() {
            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setOpaque(false);

            // Build risk column FIRST to calculate dynamic heights
            JPanel riskColumn = buildRiskColumn();

            // Now build other columns using the calculated heights
            row.add(buildCheckboxColumn());
            row.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));
            row.add(riskColumn);
            // Risk column expands to fill space - no glue needed here
            row.add(buildLastUpdatedColumn());
            row.add(Box.createHorizontalGlue());  // Push Actions to right edge
            row.add(buildActionsColumn());

            return row;
        }

        private JPanel buildCheckboxColumn() {
            JPanel panel = createCheckboxColumnPanel();
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapper.setOpaque(false);
            wrapper.add(selectCheckBox);
            panel.add(wrapper, BorderLayout.NORTH);  // Align checkbox to top
            return panel;
        }

        private JPanel buildRiskColumn() {
            JPanel riskPanel = buildRiskContent();

            // Use a custom panel that constrains width but allows dynamic height
            JPanel container = new JPanel(new BorderLayout()) {
                @Override
                public Dimension getPreferredSize() {
                    Dimension contentPref = riskPanel.getPreferredSize();
                    // Match header column preferred width (500), but use content's height
                    return new Dimension(JBUI.scale(500), contentPref.height);
                }

                @Override
                public Dimension getMinimumSize() {
                    Dimension contentMin = riskPanel.getMinimumSize();
                    // Match header column minimum width (400), but use content's height
                    return new Dimension(JBUI.scale(400), contentMin.height);
                }

                @Override
                public Dimension getMaximumSize() {
                    Dimension contentMax = riskPanel.getMaximumSize();
                    // Allow horizontal expansion, use content's max height
                    return new Dimension(Integer.MAX_VALUE, contentMax.height);
                }
            };
            container.setOpaque(false);
            container.add(riskPanel, BorderLayout.CENTER);
            return container;
        }

        private JPanel buildLastUpdatedColumn() {
            JPanel panel = createVerticalColumnPanel();
            panel.add(createTopSpacer());
            JPanel middleWrapper = createMiddleWrapper();
            JLabel label = new JLabel(formatRelativeDate(entry.dateAdded));
            label.setFont(new Font(FONT_FAMILY_MENLO, Font.PLAIN, 14));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            middleWrapper.add(label);
            panel.add(middleWrapper);
            return panel;
        }

        private JPanel buildActionsColumn() {
            JPanel panel = createVerticalColumnPanel();
            panel.add(createTopSpacer());
            JPanel middleWrapper = createMiddleWrapper();
            middleWrapper.add(createReviveButton());
            panel.add(middleWrapper);
            return panel;
        }

        // ---------- Column Layout Helpers ----------

        /**
         * Creates a vertical BoxLayout panel with standard column sizing (120-160px width).
         */
        private JPanel createVerticalColumnPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setOpaque(false);
            setColumnSizes(panel, getCalculatedRowHeight());
            return panel;
        }

        /**
         * Creates a spacer panel to skip past the title row (aligns content with description).
         */
        private JPanel createTopSpacer() {
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            Dimension size = new Dimension(Integer.MAX_VALUE, JBUI.scale(TOP_LINE_HEIGHT));
            spacer.setPreferredSize(size);
            spacer.setMinimumSize(new Dimension(0, JBUI.scale(TOP_LINE_HEIGHT)));
            spacer.setMaximumSize(size);
            return spacer;
        }

        /**
         * Creates a wrapper panel for middle section content (aligned with description row).
         */
        private JPanel createMiddleWrapper() {
            JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, JBUI.scale(2)));
            wrapper.setOpaque(false);
            Dimension size = new Dimension(Integer.MAX_VALUE, JBUI.scale(actualDescHeight + 4));
            wrapper.setPreferredSize(size);
            wrapper.setMinimumSize(new Dimension(0, JBUI.scale(DESC_LINE_HEIGHT_MIN)));
            wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(DESC_LINE_HEIGHT_MAX + 4)));
            return wrapper;
        }

        /**
         * Creates the Revive button (restores an ignored finding to active state).
         */
        private JButton createReviveButton() {
            JButton btn = new JButton(CxIcons.Ignored.REVIVE);
            btn.setBorder(JBUI.Borders.empty());  // Simplified border creation
            btn.setContentAreaFilled(false);
            btn.setFocusPainted(false);
            btn.setOpaque(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                LOGGER.info("Revive clicked for: " + (entry.packageName != null ? entry.packageName : "unknown"));
                new IgnoreManager(project).reviveSingleEntry(entry);
                clearSelection();
            });
            return btn;
        }

        // ---------- Risk Panel Content ----------

        // Height constants for each section
        private static final int TOP_LINE_HEIGHT = 50;          // Title line with icons (FIXED)
        private static final int DESC_LINE_HEIGHT_MAX = 36;     // Max 2 lines of text (18px per line)
        private static final int DESC_LINE_HEIGHT_MIN = 18;     // Min 1 line of text
        private static final int DESC_MAX_LINES = 2;            // Maximum lines for description
        private static final int BOTTOM_LINE_HEIGHT_MIN = 40;   // Min height for engine chip + file buttons
        private static final int BOTTOM_LINE_ITEM_HEIGHT = 32;  // Height per row of file buttons

        // Dynamic heights calculated during buildRiskContent()
        private int actualDescHeight = DESC_LINE_HEIGHT_MAX;
        private int actualBottomHeight = BOTTOM_LINE_HEIGHT_MIN;

        /**
         * Builds the Risk column content: title line, description, and file buttons.
         */
        private JPanel buildRiskContent() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            // 1. Top line: card icon + severity icon + name (fixed 50px height)
            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)));
            topLine.setOpaque(false);
            topLine.add(new JLabel(getCardIcon()));
            topLine.add(new JLabel(getSeverityIcon()));
            String name = formatDisplayName();
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(new Font(FONT_FAMILY_MENLO, Font.BOLD, 14));
            nameLabel.setToolTipText(name);
            topLine.add(nameLabel);
            setFlexibleHeight(topLine, TOP_LINE_HEIGHT, TOP_LINE_HEIGHT);
            panel.add(topLine);

            // 2. Description line: dynamic height (1-2 lines based on content)
            String descText = getDescriptionText(), truncatedDesc = truncateDescription(descText);
            JTextArea descArea = new JTextArea(truncatedDesc);
            descArea.setFont(new Font(FONT_FAMILY_MENLO, Font.PLAIN, 14));
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setEditable(false);
            descArea.setOpaque(false);
            descArea.setToolTipText(descText);
            actualDescHeight = calculateDescriptionHeight(truncatedDesc, descArea.getFont());
            setFlexibleHeight(descArea, actualDescHeight, DESC_LINE_HEIGHT_MAX);

            JPanel descLine = new JPanel(new BorderLayout());
            descLine.setOpaque(false);
            descLine.setBorder(JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(8), JBUI.scale(2), 0));
            descLine.add(descArea, BorderLayout.CENTER);
            setFlexibleHeight(descLine, actualDescHeight + 4, DESC_LINE_HEIGHT_MAX + 4);
            panel.add(descLine);

            // 3. Bottom line: engine chip + file buttons (expands for multiple rows)
            JPanel bottomLine = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)));
            bottomLine.setOpaque(false);
            JLabel engineChip = new JLabel(getEngineChipIcon());
            bottomLine.add(engineChip);
            addFileButtonsToContainer(bottomLine, engineChip);
            actualBottomHeight = calculateBottomLineHeight(bottomLine);
            bottomLine.setMinimumSize(new Dimension(0, JBUI.scale(BOTTOM_LINE_HEIGHT_MIN)));
            panel.add(bottomLine);

            return panel;
        }

        /**
         * Sets flexible height sizing (min=pref=height, max allows expansion).
         */
        private void setFlexibleHeight(JComponent c, int height, int maxHeight) {
            c.setPreferredSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(height)));
            c.setMinimumSize(new Dimension(0, JBUI.scale(height)));
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(maxHeight)));
        }

        /**
         * Calculates the height needed for the description based on text length.
         * Returns height for 1-3 lines depending on content.
         */
        private int calculateDescriptionHeight(String text, Font font) {
            if (text == null || text.isEmpty()) {
                return DESC_LINE_HEIGHT_MIN;
            }

            // Get font metrics for accurate line height calculation
            FontMetrics fm = getFontMetrics(font);
            int lineHeight = fm.getHeight();

            // Estimate number of lines based on character count
            // With expanded Risk column, allow 120 chars per line (matching truncateToLines)
            int charsPerLine = 120;
            int estimatedLines = (int) Math.ceil((double) text.length() / charsPerLine);

            // Also count actual newlines in the text
            int newlineCount = (int) text.chars().filter(c -> c == '\n').count();
            estimatedLines = Math.max(estimatedLines, newlineCount + 1);

            // Clamp between 1 and DESC_MAX_LINES lines
            int actualLines = Math.min(DESC_MAX_LINES, Math.max(1, estimatedLines));

            return actualLines * lineHeight;
        }

        /**
         * Calculates the height needed for the bottom line based on file buttons.
         * Returns minimum height if few buttons, expands for multiple rows.
         */
        private int calculateBottomLineHeight(JPanel fileButtonsPanel) {
            // Count the number of file buttons
            int buttonCount = 0;
            for (Component comp : fileButtonsPanel.getComponents()) {
                if (comp instanceof JButton) {
                    buttonCount++;
                }
            }

            // Estimate buttons per row based on expanded Risk column width
            // With wider column (~600-800px available), more buttons fit per row
            // Assuming ~120px per button (including spacing), ~600px available width
            int buttonsPerRow = 5;
            int rows = (int) Math.ceil((double) Math.max(1, buttonCount) / buttonsPerRow);

            // Calculate height: base height + additional rows
            return BOTTOM_LINE_HEIGHT_MIN + (Math.max(0, rows - 1) * BOTTOM_LINE_ITEM_HEIGHT);
        }

        /**
         * Returns the total calculated row height for this entry.
         */
        private int getCalculatedRowHeight() {
            return TOP_LINE_HEIGHT + actualDescHeight + 4 + actualBottomHeight;
        }

        /**
         * Truncates text to DESC_MAX_LINES lines (~120 chars/line). Adds "..." if truncated.
         */
        private String truncateDescription(String text) {
            if (text == null || text.isEmpty()) return text;
            // 120 chars per line * DESC_MAX_LINES (2) = 240 chars max
            int maxChars = DESC_MAX_LINES * 120;
            if (text.length() <= maxChars) return text;
            // Truncate with ellipsis, try to break at word boundary
            String truncated = text.substring(0, maxChars - 3);
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > maxChars - 18) truncated = truncated.substring(0, lastSpace);
            return truncated + "...";
        }

        /**
         * Returns the description text. For OSS with MALICIOUS severity, shows special message.
         */
        private String getDescriptionText() {
            // Special case: OSS/SCA with MALICIOUS severity
            if (entry.type == ScanEngine.OSS && "MALICIOUS".equalsIgnoreCase(entry.severity)) {
                return Bundle.message(Resource.IGNORED_MALICIOUS_PACKAGE_DESC);
            }
            // Default: use description if available, otherwise fallback
            return (entry.description != null && !entry.description.isBlank())
                    ? entry.description : Bundle.message(Resource.IGNORED_DESCRIPTION_NOT_AVAILABLE);
        }

        // ---------- Icon Helpers ----------

        private Icon getSeverityIcon() {
            if (entry.severity == null) return CxIcons.Small.UNKNOWN;
            return SEVERITY_ICONS.getOrDefault(entry.severity.toLowerCase(), CxIcons.Small.UNKNOWN);
        }

        private Icon getCardIcon() {
            String sev = entry.severity != null ? entry.severity.toLowerCase() : "medium";
            switch (entry.type) {
                case OSS:
                    return selectCardIcon(CxIcons.Ignored.CARD_PACKAGE_CRITICAL, CxIcons.Ignored.CARD_PACKAGE_HIGH,
                            CxIcons.Ignored.CARD_PACKAGE_MEDIUM, CxIcons.Ignored.CARD_PACKAGE_LOW, CxIcons.Ignored.CARD_PACKAGE_MALICIOUS, sev);
                case SECRETS:
                    return selectCardIcon(CxIcons.Ignored.CARD_SECRET_CRITICAL, CxIcons.Ignored.CARD_SECRET_HIGH,
                            CxIcons.Ignored.CARD_SECRET_MEDIUM, CxIcons.Ignored.CARD_SECRET_LOW, CxIcons.Ignored.CARD_SECRET_MALICIOUS, sev);
                case CONTAINERS:
                    return selectCardIcon(CxIcons.Ignored.CARD_CONTAINERS_CRITICAL, CxIcons.Ignored.CARD_CONTAINERS_HIGH,
                            CxIcons.Ignored.CARD_CONTAINERS_MEDIUM, CxIcons.Ignored.CARD_CONTAINERS_LOW, CxIcons.Ignored.CARD_CONTAINERS_MALICIOUS, sev);
                default:
                    return selectCardIcon(CxIcons.Ignored.CARD_VULNERABILITY_CRITICAL, CxIcons.Ignored.CARD_VULNERABILITY_HIGH,
                            CxIcons.Ignored.CARD_VULNERABILITY_MEDIUM, CxIcons.Ignored.CARD_VULNERABILITY_LOW, CxIcons.Ignored.CARD_VULNERABILITY_MALICIOUS, sev);
            }
        }

        /**
         * Selects the appropriate card icon based on severity.
         */
        private Icon selectCardIcon(Icon critical, Icon high, Icon medium, Icon low, Icon malicious, String sev) {
            switch (sev) {
                case "critical":
                    return critical;
                case "high":
                    return high;
                case "low":
                    return low;
                case "malicious":
                    return malicious;
                default:
                    return medium;
            }
        }

        private Icon getEngineChipIcon() {
            return ENGINE_CHIP_ICONS.getOrDefault(entry.type, CxIcons.Ignored.ENGINE_CHIP_SCA);
        }

        // ---------- File Buttons ----------

        /**
         * Adds file buttons directly to the container (which also contains the engine chip).
         * This ensures the engine chip and file buttons are on the same line using WrapLayout.
         *
         * @param container  the container to add buttons to (uses WrapLayout)
         * @param engineChip the engine chip label (first component, preserved during collapse)
         */
        private void addFileButtonsToContainer(JPanel container, JLabel engineChip) {
            List<IgnoreEntry.FileReference> activeFiles = entry.files != null
                    ? entry.files.stream().filter(f -> f != null && f.active).collect(Collectors.toList())
                    : List.of();

            if (activeFiles.isEmpty()) {
                JLabel none = new JLabel(Bundle.message(Resource.IGNORED_NO_FILES));
                none.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
                container.add(none);
                return;
            }

            // Add first file button (always visible)
            JButton firstFileButton = createFileButton(activeFiles.get(0));
            container.add(firstFileButton);

            if (activeFiles.size() > 1) {
                List<IgnoreEntry.FileReference> hidden = activeFiles.subList(1, activeFiles.size());
                JLabel expand = createUnderlinedLink(Bundle.message(Resource.IGNORED_MORE_FILES, hidden.size()));

                // Create "See less" link (will be added after expansion)
                JLabel collapse = createUnderlinedLink(Bundle.message(Resource.IGNORED_LESS_FILES));

                expand.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        // Remove the expand link
                        container.remove(expand);
                        // Add all hidden file buttons
                        hidden.forEach(f -> container.add(createFileButton(f)));
                        // Add the collapse link at the end
                        container.add(collapse);
                        propagateRevalidateForExpansion(container);
                    }
                });

                collapse.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        // Remove all components except the engine chip and first file button
                        container.removeAll();
                        // Re-add engine chip, first file button and expand link
                        container.add(engineChip);
                        container.add(firstFileButton);
                        container.add(expand);
                        propagateRevalidateForExpansion(container);
                    }
                });

                container.add(expand);
            }
        }

        /**
         * Creates a file button with pill shape styling and file icon.
         */
        private JButton createFileButton(IgnoreEntry.FileReference file) {
            JButton btn = createPillButton(formatFileLabel(file));
            btn.setToolTipText(file.path + (file.line != null ? ":" + file.line : ""));
            btn.addActionListener(ev -> navigateToFile(file));
            return btn;
        }

        /**
         * Creates a pill-shaped button with FILE_ICON, rounded corners, and theme-aware styling.
         */
        private JButton createPillButton(String text) {
            JButton btn = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    // Draw rounded pill background with theme-aware colors
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int arc = getHeight();
                    // Fill background (JBColor auto-switches for dark/light theme)
                    g2.setColor(PILL_BG);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    // Draw border
                    g2.setColor(PILL_BORDER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                    g2.dispose();
                    super.paintComponent(g);
                }

                @Override
                public Color getForeground() {
                    return TEXT_COLOR;
                }
            };
            // Configure pill button appearance
            btn.setIcon(CxIcons.Ignored.FILE_ICON);
            btn.setIconTextGap(JBUI.scale(3));
            btn.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, JBUI.scale(12)));
            btn.setBorder(JBUI.Borders.empty());
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Set fixed height for consistent pill appearance
            int pillHeight = JBUI.scale(24);
            btn.setPreferredSize(new Dimension(btn.getPreferredSize().width, pillHeight));
            btn.setMinimumSize(new Dimension(0, pillHeight));
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, pillHeight));
            return btn;
        }

        /**
         * Creates an underlined text link for expand/collapse actions.
         */
        private JLabel createUnderlinedLink(String text) {
            JLabel label = new JLabel("<html><u>" + text + "</u></html>");
            label.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, 12));
            label.setForeground(LINK_COLOR);
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setBorder(JBUI.Borders.empty(4));
            return label;
        }

        /**
         * Revalidates hierarchy from container up to scroll pane for expand/collapse.
         */
        private void propagateRevalidateForExpansion(Container container) {
            // Walk up hierarchy, revalidating each level until we hit the scroll pane
            for (Container c = container; c != null; c = c.getParent()) {
                c.revalidate();
                if (c instanceof JScrollPane) {
                    c.repaint();
                    break;
                }
            }
            // Force immediate layout recalculation on this panel
            invalidate();
            validate();
            repaint();
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
                // Only show filename, not line number (line number is shown in tooltip)
                return Paths.get(f.path).getFileName().toString();
            } catch (Exception ex) {
                return f.line != null ? f.path + ":" + f.line : f.path;
            }
        }

        private String formatDisplayName() {
            String name = entry.packageName != null ? entry.packageName : Bundle.message(Resource.IGNORED_UNKNOWN);
            if (entry.type == ScanEngine.OSS) {
                String mgr = entry.packageManager != null ? entry.packageManager : "pkg";
                String ver = entry.packageVersion != null && !entry.packageVersion.isEmpty() ? "@" + entry.packageVersion : "";
                return mgr + "@" + name + ver;
            } else if (entry.type == ScanEngine.ASCA) {
                return entry.title != null ? entry.title : name;
            } else if (entry.type == ScanEngine.CONTAINERS) {
                String tag = entry.imageTag != null ? entry.imageTag : entry.packageVersion;
                return name + (tag != null && !tag.isEmpty() ? "@" + tag : "");
            }
            return name;
        }

        private String formatRelativeDate(String isoDate) {
            if (isoDate == null || isoDate.isEmpty()) return Bundle.message(Resource.IGNORED_UNKNOWN);
            try {
                long days = ChronoUnit.DAYS.between(ZonedDateTime.parse(isoDate).toLocalDate(), ZonedDateTime.now().toLocalDate());
                if (days == 0) return Bundle.message(Resource.IGNORED_TODAY);
                if (days < 2) return "1 day ago";
                if (days < 7) return days + " days ago";
                if (days < 30) return (days / 7) + " weeks ago";
                return days < 365 ? (days / 30) + " months ago" : (days / 365) + " years ago";
            } catch (Exception ex) {
                return isoDate;
            }
        }

        // ---------- UI Helpers ----------

        private static final int CHECKBOX_COL_WIDTH = 50;

        /**
         * Creates a panel for the checkbox column with dynamic dimensions based on row content.
         */
        private JPanel createCheckboxColumnPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            int dynamicHeight = JBUI.scale(getCalculatedRowHeight());
            panel.setPreferredSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), dynamicHeight));
            panel.setMinimumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), dynamicHeight));
            panel.setMaximumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), Integer.MAX_VALUE)); // Allow expansion
            return panel;
        }

        /**
         * Sets column sizing: 120-160px width, dynamic height based on row content.
         */
        private void setColumnSizes(JPanel panel, int minH) {
            panel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(minH)));
            panel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(getCalculatedRowHeight())));
            panel.setMaximumSize(new Dimension(JBUI.scale(160), Integer.MAX_VALUE));
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

        public boolean isSelected() {
            return selectCheckBox.isSelected();
        }

        public void setSelected(boolean selected) {
            selectCheckBox.setSelected(selected);
        }
    }
}
