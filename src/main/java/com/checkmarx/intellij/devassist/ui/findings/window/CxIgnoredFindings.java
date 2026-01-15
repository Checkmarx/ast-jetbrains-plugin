package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.ui.actions.IgnoredFindingsToolbarActions;
import com.checkmarx.intellij.devassist.ui.layout.WrapLayout;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.CommonPanels;
import com.checkmarx.intellij.tool.window.DevAssistPromotionalPanel;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
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
    private static final String FONT_FAMILY_MENLO = "Menlo";
    private static final String FONT_FAMILY_INTER = "Inter";
    private static final String FONT_FAMILY_SF_PRO = "SF Pro";

    // ========== Instance Fields ==========
    private final Project project;
    private final Content content;
    private final JPanel ignoredListPanel = new JPanel();
    private final List<IgnoredEntryPanel> entryPanels = new ArrayList<>();

    private JCheckBox selectAllCheckbox;
    private JPanel headerPanel;
    private JPanel selectionBarPanel;
    private JPanel columnsPanel;
    private JLabel selectionCountLabel;
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
        try {
            if (!new GlobalSettingsComponent().isValid()) {
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
                    // No license - show promotional panel (only cube image, no toolbar, no count)
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

    /** Creates a header with selection bar and column titles: Checkbox, Risk, Last Updated, Actions. */
    private JPanel createHeaderPanel() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerPanel.setBorder(JBUI.Borders.empty(12, 12, 0, 12));

        // Create selection bar (hidden by default)
        selectionBarPanel = createSelectionBar();
        selectionBarPanel.setVisible(false);

        // Create columns panel
        columnsPanel = new JPanel();
        columnsPanel.setLayout(new BoxLayout(columnsPanel, BoxLayout.X_AXIS));
        columnsPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        columnsPanel.setBorder(JBUI.Borders.empty(12, 0, 8, 0));

        // Checkbox | Risk (expands) | Last Updated | Actions
        columnsPanel.add(createFixedColumn(50, createSelectAllCheckbox()));
        columnsPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));
        columnsPanel.add(createFlexibleColumn(Bundle.message(Resource.IGNORED_RISK_COLUMN), 400, 500, Integer.MAX_VALUE, FlowLayout.LEFT, FONT_FAMILY_INTER));
        // Risk column expands to fill space - no glue needed here
        // Use HTML to prevent text wrapping in header
        columnsPanel.add(createFlexibleColumn("<html><nobr>" + Bundle.message(Resource.IGNORED_LAST_UPDATED_COLUMN) + "</nobr></html>", 120, 140, 160, FlowLayout.CENTER, FONT_FAMILY_SF_PRO));
        columnsPanel.add(Box.createHorizontalGlue());  // Push Actions to right edge
        columnsPanel.add(createFixedColumn(140, null));

        // Container for both selection bar and columns (stacked vertically)
        JPanel headerContent = new JPanel();
        headerContent.setLayout(new BoxLayout(headerContent, BoxLayout.Y_AXIS));
        headerContent.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerContent.add(selectionBarPanel);
        headerContent.add(columnsPanel);

        headerPanel.add(headerContent, BorderLayout.CENTER);
        return headerPanel;
    }

    /** Creates the selection bar that appears when items are selected. */
    private JPanel createSelectionBar() {
        // Use BoxLayout for horizontal alignment
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBackground(JBUI.CurrentTheme.ToolWindow.background());

        // Fixed height of 56px: 8px (top padding) + 40px (content) + 8px (bottom padding)
        bar.setBorder(JBUI.Borders.empty(8, 0, 8, 0));
        final int SELECTION_BAR_HEIGHT = 56;
        bar.setPreferredSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(SELECTION_BAR_HEIGHT)));
        bar.setMinimumSize(new Dimension(0, JBUI.scale(SELECTION_BAR_HEIGHT)));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(SELECTION_BAR_HEIGHT)));

        // Width to fit the revive-selected button SVG (174x28)
        final int REVIVE_SELECTED_BTN_WIDTH = 180;

        // === "N Risks selected" - simple text with natural width ===
        selectionCountLabel = new JLabel("0 Risks selected");
        selectionCountLabel.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, 14));
        selectionCountLabel.setForeground(UIManager.getColor("Label.foreground"));

        // Content height is 40px (56px total - 8px top padding - 8px bottom padding)
        final int CONTENT_HEIGHT = 40;

        // === Vertical divider line ===
        JPanel divider = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(0xADADAD));
                g.drawLine(getWidth() / 2, 8, getWidth() / 2, getHeight() - 8);
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(new Dimension(JBUI.scale(24), JBUI.scale(CONTENT_HEIGHT)));
        divider.setMinimumSize(new Dimension(JBUI.scale(24), JBUI.scale(CONTENT_HEIGHT)));
        divider.setMaximumSize(new Dimension(JBUI.scale(24), JBUI.scale(CONTENT_HEIGHT)));

        // === "X Clear Selections" button ===
        JLabel clearSelectionBtn = new JLabel(CxIcons.Ignored.CLEAR_SELECTION);
        clearSelectionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearSelectionBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clearSelection();
            }
        });

        // === "Revive Selected" button - aligned to right ===
        JPanel reviveSelectedPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        reviveSelectedPanel.setOpaque(false);
        reviveSelectedPanel.setPreferredSize(new Dimension(JBUI.scale(REVIVE_SELECTED_BTN_WIDTH), JBUI.scale(CONTENT_HEIGHT)));
        reviveSelectedPanel.setMinimumSize(new Dimension(JBUI.scale(REVIVE_SELECTED_BTN_WIDTH), JBUI.scale(CONTENT_HEIGHT)));
        reviveSelectedPanel.setMaximumSize(new Dimension(JBUI.scale(REVIVE_SELECTED_BTN_WIDTH), JBUI.scale(CONTENT_HEIGHT)));

        JLabel reviveSelectedBtn = new JLabel(CxIcons.Ignored.REVIVE_SELECTED);
        reviveSelectedBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reviveSelectedBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                reviveSelectedEntries();
            }
        });
        reviveSelectedPanel.add(reviveSelectedBtn);

        // Add components with proper spacing
        bar.add(selectionCountLabel);
        bar.add(Box.createRigidArea(new Dimension(JBUI.scale(16), 0))); // Padding before divider
        bar.add(divider);
        bar.add(Box.createRigidArea(new Dimension(JBUI.scale(8), 0))); // Padding after divider
        bar.add(clearSelectionBtn);
        bar.add(Box.createHorizontalGlue()); // Push Revive Selected to the right
        bar.add(reviveSelectedPanel);

        return bar;
    }

    /** Revives all selected entries. */
    private void reviveSelectedEntries() {
        List<IgnoredEntryPanel> selected = entryPanels.stream()
                .filter(IgnoredEntryPanel::isSelected)
                .collect(Collectors.toList());
        LOGGER.info("Revive selected clicked for " + selected.size() + " entries");
        // TODO: Implement actual revive logic
    }

    /** Clears all selections. */
    private void clearSelection() {
        entryPanels.forEach(panel -> panel.setSelected(false));
        updateSelectionState();
    }

    /** Updates the selection bar visibility and count based on current selections. */
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

    @SuppressWarnings("MagicConstant")
    private JPanel createFlexibleColumn(String title, int min, int pref, int max, int alignment, String fontFamily) {
        JPanel panel = new JPanel(new FlowLayout(alignment, alignment == FlowLayout.LEFT ? JBUI.scale(20) : 0, 0));
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(JBUI.scale(min), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setPreferredSize(new Dimension(JBUI.scale(pref), JBUI.scale(HEADER_ROW_HEIGHT)));
        panel.setMaximumSize(new Dimension(JBUI.scale(max), JBUI.scale(HEADER_ROW_HEIGHT)));

        JLabel label = new JLabel(title);
        label.setFont(new Font(fontFamily, Font.PLAIN, 14));
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
        updateSelectionState();
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
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            setColumnSizes(panel, 120, 140, 160, getCalculatedRowHeight());

            JLabel label = new JLabel(formatRelativeDate(entry.dateAdded));
            label.setFont(new Font(FONT_FAMILY_MENLO, Font.PLAIN, 14));
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.CENTER);  // Align to top for dynamic height
            panel.add(label, BorderLayout.CENTER);
            return panel;
        }

        private JPanel buildActionsColumn() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setOpaque(false);
            setColumnSizes(panel, 120, 140, 160, getCalculatedRowHeight());

            JButton reviveButton = new JButton(CxIcons.Ignored.REVIVE);
            reviveButton.setBorder(BorderFactory.createEmptyBorder());
            reviveButton.setContentAreaFilled(false);
            reviveButton.setFocusPainted(false);
            reviveButton.setOpaque(false);
            reviveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reviveButton.addActionListener(ev ->
                    LOGGER.info("Revive clicked for: " + (entry.packageName != null ? entry.packageName : "unknown")));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.FIRST_LINE_START;
            gbc.insets = JBUI.insetsTop(10);
            panel.add(reviveButton, gbc);
            return panel;
        }

        // ---------- Risk Panel Content ----------

        // Height constants for each section
        private static final int TOP_LINE_HEIGHT = 50;          // Title line with icons (FIXED)
        private static final int DESC_LINE_HEIGHT_MAX = 36;     // Max 2 lines of text (18px per line)
        private static final int DESC_LINE_HEIGHT_MIN = 18;     // Min 1 line of text
        private static final int DESC_MAX_LINES = 2;            // Maximum lines for description
        private static final int BOTTOM_LINE_HEIGHT_MIN = 40;   // Min height for engine chip + file buttons (increased for button visibility)
        private static final int BOTTOM_LINE_ITEM_HEIGHT = 32;  // Height per row of file buttons (increased for proper button display)

        // Dynamic heights calculated during buildRiskContent()
        private int actualDescHeight = DESC_LINE_HEIGHT_MAX;
        private int actualBottomHeight = BOTTOM_LINE_HEIGHT_MIN;

        private JPanel buildRiskContent() {
            // Use BoxLayout with dynamic-height sections
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            // 1. Top line: card icon + severity icon + name (FIXED HEIGHT = 50px)
            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4)));
            topLine.setOpaque(false);
            topLine.add(new JLabel(getCardIcon()));
            topLine.add(new JLabel(getSeverityIcon()));

            String name = formatDisplayName();
            JLabel nameLabel = new JLabel(name);
            nameLabel.setFont(new Font(FONT_FAMILY_MENLO, Font.BOLD, 14));
            nameLabel.setToolTipText(name);
            topLine.add(nameLabel);
            // Fix topLine height
            Dimension topLineSize = new Dimension(Integer.MAX_VALUE, JBUI.scale(TOP_LINE_HEIGHT));
            topLine.setPreferredSize(topLineSize);
            topLine.setMinimumSize(new Dimension(0, JBUI.scale(TOP_LINE_HEIGHT)));
            topLine.setMaximumSize(topLineSize);
            panel.add(topLine);

            // 2. Description line: DYNAMIC HEIGHT (shrinks for short text, max DESC_MAX_LINES lines)
            String descText = getDescriptionText();
            String truncatedDesc = truncateToLines(descText, DESC_MAX_LINES);
            JTextArea descArea = new JTextArea(truncatedDesc);
            descArea.setFont(new Font(FONT_FAMILY_MENLO, Font.PLAIN, 14));
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descArea.setEditable(false);
            descArea.setOpaque(false);
            descArea.setToolTipText(descText);  // Full text in tooltip

            // Calculate actual description height based on content
            actualDescHeight = calculateDescriptionHeight(truncatedDesc, descArea.getFont());

            Dimension descSize = new Dimension(Integer.MAX_VALUE, JBUI.scale(actualDescHeight));
            descArea.setPreferredSize(descSize);
            descArea.setMinimumSize(new Dimension(0, JBUI.scale(actualDescHeight)));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(DESC_LINE_HEIGHT_MAX)));

            JPanel descLine = new JPanel(new BorderLayout());
            descLine.setOpaque(false);
            descLine.setBorder(JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(8), JBUI.scale(2), 0));
            descLine.add(descArea, BorderLayout.CENTER);
            // Dynamic descLine height (descArea height + padding)
            int descLineHeight = JBUI.scale(actualDescHeight + 4);
            descLine.setPreferredSize(new Dimension(Integer.MAX_VALUE, descLineHeight));
            descLine.setMinimumSize(new Dimension(0, descLineHeight));
            descLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(DESC_LINE_HEIGHT_MAX + 4)));
            panel.add(descLine);

            // 3. Bottom line: engine chip + file buttons (DYNAMIC HEIGHT - expands for multiple file rows)
            // Use WrapLayout for the entire bottom line so chip and buttons flow together on same line
            JPanel bottomLine = new JPanel(new WrapLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4)));
            bottomLine.setOpaque(false);

            // Add engine chip first - it will be on the same line as file buttons
            JLabel engineChip = new JLabel(getEngineChipIcon());
            bottomLine.add(engineChip);

            // Add file buttons directly to bottomLine
            addFileButtonsToContainer(bottomLine, engineChip);

            // Calculate actual bottom height based on content (for initial sizing)
            actualBottomHeight = calculateBottomLineHeight(bottomLine);

            // Set minimum height, but let the layout manager determine actual height
            bottomLine.setMinimumSize(new Dimension(0, JBUI.scale(BOTTOM_LINE_HEIGHT_MIN)));
            // Don't set preferred/max height - let it grow naturally with content
            panel.add(bottomLine);

            return panel;
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
         * Truncates text to approximately the specified number of lines.
         * Adds "..." if truncated.
         * Uses character count based on expanded Risk column width.
         */
        private String truncateToLines(String text, int maxLines) {
            if (text == null || text.isEmpty()) return text;

            // With expanded Risk column, allow 120 chars per line
            // For 2 lines: 120 * 2 = 240 characters max
            int charsPerLine = 120;
            int maxChars = maxLines * charsPerLine;

            if (text.length() <= maxChars) {
                return text;
            }

            // Truncate and add ellipsis
            // Leave room for "..." (3 chars)
            String truncated = text.substring(0, maxChars - 3);
            // Try to break at a word boundary
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > (maxChars - 3) - 15) {
                truncated = truncated.substring(0, lastSpace);
            }
            return truncated + "...";
        }

        /**
         * Returns the description text based on scanner type and entry data.
         * - ASCA (SAST): Display description field if available
         * - IaC: Display description field if available
         * - Secrets: Display description field if available
         * - OSS (SCA): If severity is MALICIOUS, display "This is a malicious package!"; otherwise description or fallback
         * - Containers: Display description field if available, or fallback
         */
        private String getDescriptionText() {
            String fallback = Bundle.message(Resource.IGNORED_DESCRIPTION_NOT_AVAILABLE);

            if (entry.type == null) {
                return isNotBlank(entry.description) ? entry.description : fallback;
            }

            switch (entry.type) {
                case OSS:
                    // For OSS/SCA: if severity is MALICIOUS, show special message
                    if ("MALICIOUS".equalsIgnoreCase(entry.severity)) {
                        return Bundle.message(Resource.IGNORED_MALICIOUS_PACKAGE_DESC);
                    }
                    return isNotBlank(entry.description) ? entry.description : fallback;

                case ASCA:
                case IAC:
                case SECRETS:
                    // For ASCA, IaC, Secrets: display description if available
                    return isNotBlank(entry.description) ? entry.description : fallback;

                case CONTAINERS:
                    // For Containers: display description if available, or fallback
                    return isNotBlank(entry.description) ? entry.description : fallback;

                default:
                    return isNotBlank(entry.description) ? entry.description : fallback;
            }
        }

        private boolean isNotBlank(String str) {
            return str != null && !str.trim().isEmpty();
        }

        // ---------- Icon Helpers ----------

        private Icon getSeverityIcon() {
            if (entry.severity == null) return CxIcons.Small.UNKNOWN;
            switch (entry.severity.toLowerCase()) {
                case "critical": return CxIcons.Medium.CRITICAL;
                case "high": return CxIcons.Medium.HIGH;
                case "medium": return CxIcons.Medium.MEDIUM;
                case "low": return CxIcons.Medium.LOW;
                case "malicious": return CxIcons.Medium.MALICIOUS;
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

        /**
         * Adds file buttons directly to the container (which also contains the engine chip).
         * This ensures the engine chip and file buttons are on the same line using WrapLayout.
         *
         * @param container   the container to add buttons to (uses WrapLayout)
         * @param engineChip  the engine chip label (first component, preserved during collapse)
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
            String label = formatFileLabel(file);
            JButton btn = createPillButton(label, CxIcons.Ignored.FILE_ICON);
            btn.setToolTipText(file.path + (file.line != null ? ":" + file.line : ""));
            btn.addActionListener(ev -> navigateToFile(file));
            return btn;
        }

        /**
         * Creates a pill-shaped button with rounded corners and border styling.
         * Dark theme: filled background (#323438) with border (#43454A)
         * Light theme: transparent background with border only (#6F6F6F)
         *
         * @param text the button text
         * @param icon optional icon to display before the text (can be null)
         */
        private JButton createPillButton(String text, Icon icon) {
            JButton btn = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    boolean isDark = com.intellij.util.ui.UIUtil.isUnderDarcula();
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    int width = getWidth();
                    int height = getHeight();
                    int arc = height; // Full pill shape for both themes

                    if (isDark) {
                        // Dark theme: filled background (#323438) with border (#43454A)
                        g2.setColor(new Color(0x323438));
                        g2.fillRoundRect(0, 0, width, height, arc, arc);
                        g2.setColor(new Color(0x43454A));
                        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
                    } else {
                        // Light theme: transparent background, border only (#9DA3B4)
                        g2.setColor(new Color(0x9DA3B4));
                        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
                    }

                    g2.dispose();

                    // Paint the text and icon
                    super.paintComponent(g);
                }

                @Override
                public Color getForeground() {
                    boolean isDark = com.intellij.util.ui.UIUtil.isUnderDarcula();
                    return isDark ? new Color(0xADADAD) : new Color(0x52545F);
                }
            };

            if (icon != null) {
                btn.setIcon(icon);
                btn.setIconTextGap(JBUI.scale(3));
            }
            btn.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, JBUI.scale(12)));
            btn.setBorder(JBUI.Borders.empty(0, 0));
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setFocusPainted(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Set fixed height of 24px
            int pillHeight = JBUI.scale(24);
            btn.setPreferredSize(new Dimension(btn.getPreferredSize().width, pillHeight));
            btn.setMinimumSize(new Dimension(0, pillHeight));
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, pillHeight));

            return btn;
        }

        /**
         * Creates an underlined text link for expand/collapse actions.
         * Styled as simple underlined text (not a pill button) per Figma design.
         */
        private JLabel createUnderlinedLink(String text) {
            JLabel label = new JLabel("<html><u>" + text + "</u></html>");
            label.setFont(new Font(FONT_FAMILY_SF_PRO, Font.PLAIN, 12));
            // Use theme-specific colors per Figma: dark=#ADADAD, light=#606572
            boolean isDarkTheme = com.intellij.util.ui.UIUtil.isUnderDarcula();
            label.setForeground(isDarkTheme ? new Color(0xADADAD) : new Color(0x606572));
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setBorder(JBUI.Borders.empty(4, 4)); // Small padding for alignment
            return label;
        }

        private void propagateRevalidate(Container container) {
            // Revalidate the entire IgnoredEntryPanel to recalculate row height
            Container parent = container;
            while (parent != null) {
                if (parent instanceof IgnoredEntryPanel) {
                    parent.revalidate();
                    parent.repaint();
                    // Also revalidate the scroll pane to update scrollbar
                    Container scrollParent = parent.getParent();
                    while (scrollParent != null && !(scrollParent instanceof JScrollPane)) {
                        scrollParent = scrollParent.getParent();
                    }
                    if (scrollParent != null) {
                        scrollParent.revalidate();
                        scrollParent.repaint();
                    }
                    return;
                }
                parent = parent.getParent();
            }
        }

        /**
         * Revalidates the entire component hierarchy when file buttons are expanded/collapsed.
         * This ensures the row height properly adjusts to accommodate wrapped file buttons.
         */
        private void propagateRevalidateForExpansion(Container container) {
            // First revalidate the container itself
            container.revalidate();

            // Walk up to find the IgnoredEntryPanel and revalidate the entire hierarchy
            Container parent = container;
            while (parent != null) {
                parent.revalidate();
                if (parent instanceof IgnoredEntryPanel) {
                    // Found the entry panel - now revalidate all parents up to scroll pane
                    Container scrollParent = parent.getParent();
                    while (scrollParent != null) {
                        scrollParent.revalidate();
                        if (scrollParent instanceof JScrollPane) {
                            break;
                        }
                        scrollParent = scrollParent.getParent();
                    }
                    // Force immediate layout recalculation
                    parent.invalidate();
                    parent.validate();
                    parent.repaint();

                    // Also repaint the scroll pane
                    if (scrollParent != null) {
                        scrollParent.repaint();
                    }
                    return;
                }
                parent = parent.getParent();
            }
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
        // Default ROW_HEIGHT for columns that don't have dynamic content
        // TOP_LINE_HEIGHT(50) + DESC_LINE_HEIGHT_MAX(54) + padding(4) + BOTTOM_LINE_HEIGHT_MIN(32) = 140
        private static final int DEFAULT_ROW_HEIGHT = TOP_LINE_HEIGHT + DESC_LINE_HEIGHT_MAX + 4 + BOTTOM_LINE_HEIGHT_MIN;

        /** Creates a panel for the checkbox column with dynamic dimensions based on row content. */
        private JPanel createCheckboxColumnPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            int dynamicHeight = JBUI.scale(getCalculatedRowHeight());
            panel.setPreferredSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), dynamicHeight));
            panel.setMinimumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), dynamicHeight));
            panel.setMaximumSize(new Dimension(JBUI.scale(CHECKBOX_COL_WIDTH), Integer.MAX_VALUE)); // Allow expansion
            return panel;
        }

        /** Sets horizontal sizing with dynamic height based on row content. */
        private void setColumnSizes(JPanel panel, int minW, int prefW, int maxW, int minH) {
            int dynamicHeight = getCalculatedRowHeight();
            panel.setMinimumSize(new Dimension(JBUI.scale(minW), JBUI.scale(minH)));
            panel.setPreferredSize(new Dimension(JBUI.scale(prefW), JBUI.scale(dynamicHeight)));
            panel.setMaximumSize(new Dimension(JBUI.scale(maxW), Integer.MAX_VALUE)); // Allow expansion
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
