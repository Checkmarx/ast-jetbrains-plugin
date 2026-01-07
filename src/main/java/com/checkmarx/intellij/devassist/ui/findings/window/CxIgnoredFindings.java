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
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CxIgnoredFindings extends SimpleToolWindowPanel implements Disposable {
    private static final Logger LOGGER = Utils.getLogger(CxIgnoredFindings.class);

    private final Project project;
    private final JPanel ignoredListPanel;
    private final Content content;
    private JCheckBox selectAllCheckbox;
    private final java.util.List<IgnoredEntryPanel> entryPanels = new java.util.ArrayList<>();
    private List<IgnoreEntry> allEntries = new java.util.ArrayList<>();

    public CxIgnoredFindings(Project project, Content content) {
        super(false, true);
        this.project = project;
        this.content = content;
        this.ignoredListPanel = new JPanel();

        // Setup initial UI based on settings validity, subscribe to settings changes
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

        // Subscribe to settings changes
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)
                .subscribe(SettingsListener.SETTINGS_APPLIED, new SettingsListener() {
                    @Override
                    public void settingsApplied() {
                        settingsCheckRunnable.run();
                    }
                });

        settingsCheckRunnable.run();

        // SUBSCRIBE TO IGNORE_TOPIC UPDATES
        project.getMessageBus().connect(this)
                .subscribe(IgnoreFileManager.IGNORE_TOPIC, new IgnoreFileManager.IgnoreListener() {
                    @Override
                    public void onIgnoreUpdated() {
                        ApplicationManager.getApplication().invokeLater(() -> refreshIgnoredEntries());
                    }
                });
    }

    /**
     * Draw the authentication panel prompting the user to configure settings.
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
        revalidate();
        repaint();
    }

    /**
     * Draw the main panel with toolbar and ignored findings list.
     * Shown when global settings are valid.
     */
    private void drawMainPanel() {
        removeAll();

        // Create and set toolbar with filter actions
        ActionToolbar toolbar = createActionToolbar();
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        // Create main container with proper layout
        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setBackground(JBUI.CurrentTheme.ToolWindow.background());

        // Create header panel
        JPanel headerPanel = createHeaderPanel();
        mainContainer.add(headerPanel, BorderLayout.NORTH);

        // Create content area
        ignoredListPanel.removeAll();
        ignoredListPanel.setLayout(new BoxLayout(ignoredListPanel, BoxLayout.Y_AXIS));
        ignoredListPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());

        JBScrollPane scrollPane = new JBScrollPane(ignoredListPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainContainer.add(scrollPane, BorderLayout.CENTER);

        setContent(mainContainer);
        refreshIgnoredEntries();

        // Auto-refresh every 5 seconds
        Timer refreshTimer = new Timer(5000, e -> refreshIgnoredEntries());
        refreshTimer.start();

        revalidate();
        repaint();
    }

    /**
     * Create action toolbar with filter buttons
     */
    private ActionToolbar createActionToolbar() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // Add severity filter actions
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityMaliciousFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityCriticalFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityHighFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityMediumFilter());
        actionGroup.add(new VulnerabilityFilterBaseAction.VulnerabilityLowFilter());

        return ActionManager.getInstance().createActionToolbar("CxIgnoredFindings", actionGroup, true);
    }

    /**
     * Apply current filters and refresh the display
     */
    private void applyFiltersAndRefresh() {
        if (!new GlobalSettingsComponent().isValid()) {
            return; // Don't filter if not authenticated
        }

        Set<Filterable> activeFilters = VulnerabilityFilterState.getInstance().getFilters();

        // If no filters active, show all entries
        if (activeFilters.isEmpty()) {
            displayFilteredEntries(allEntries);
            return;
        }

        // Filter entries based on severity
        List<IgnoreEntry> filteredEntries = allEntries.stream()
                .filter(entry -> shouldShowEntry(entry, activeFilters))
                .collect(Collectors.toList());

        displayFilteredEntries(filteredEntries);
    }

    /**
     * Check if entry should be shown based on active filters
     */
    private boolean shouldShowEntry(IgnoreEntry entry, Set<Filterable> activeFilters) {
        String severity = entry.severity;
        if (severity == null) {
            return true; // Show entries with unknown severity
        }

        return activeFilters.stream()
                .anyMatch(filter -> filter.getFilterValue().equalsIgnoreCase(severity));
    }

    /**
     * Display the filtered list of entries
     */
    private void displayFilteredEntries(List<IgnoreEntry> entries) {
        ignoredListPanel.removeAll();
        entryPanels.clear();

        if (entries.isEmpty()) {
            JPanel emptyPanel = new JPanel(new BorderLayout());
            emptyPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
            emptyPanel.setBorder(JBUI.Borders.empty(40));

            JLabel emptyLabel = new JLabel("No ignored vulnerabilities", SwingConstants.CENTER);
            emptyLabel.setFont(JBUI.Fonts.label(14));
            emptyLabel.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            emptyPanel.add(emptyLabel, BorderLayout.CENTER);

            ignoredListPanel.add(emptyPanel);
        } else {
            for (IgnoreEntry entry : entries) {
                IgnoredEntryPanel panel = new IgnoredEntryPanel(entry, project);
                entryPanels.add(panel);
                ignoredListPanel.add(panel);
            }
        }

        // Reset select all checkbox
        if (selectAllCheckbox != null) {
            selectAllCheckbox.setSelected(false);
        }

        ignoredListPanel.revalidate();
        ignoredListPanel.repaint();
    }

    /**
     * Create the header panel with column titles
     */
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        headerPanel.setBorder(JBUI.Borders.empty(12, 12, 0, 12));

        // Create header with fixed-width columns matching row structure
        JPanel columnHeaderPanel = new JPanel();
        columnHeaderPanel.setLayout(new BoxLayout(columnHeaderPanel, BoxLayout.X_AXIS));
        columnHeaderPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());
        columnHeaderPanel.setBorder(JBUI.Borders.empty(12, 0, 8, 0));

        // 1) Checkbox column header - fixed width matching row
        JPanel checkboxHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        checkboxHeaderPanel.setOpaque(false);
        checkboxHeaderPanel.setPreferredSize(new Dimension(JBUI.scale(50), JBUI.scale(30)));
        checkboxHeaderPanel.setMinimumSize(new Dimension(JBUI.scale(50), JBUI.scale(30)));
        checkboxHeaderPanel.setMaximumSize(new Dimension(JBUI.scale(50), JBUI.scale(30)));

        selectAllCheckbox = new JCheckBox();
        selectAllCheckbox.setOpaque(false);
        selectAllCheckbox.addActionListener(e -> toggleSelectAll());
        checkboxHeaderPanel.add(selectAllCheckbox);
        columnHeaderPanel.add(checkboxHeaderPanel);
        // Add space between checkbox and risk column
        columnHeaderPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));

        // 2) Risk column header - takes reasonable space
        JPanel riskHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(20), 0));
        riskHeaderPanel.setOpaque(false);
        riskHeaderPanel.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(30)));
        riskHeaderPanel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(30)));
        riskHeaderPanel.setMaximumSize(new Dimension(JBUI.scale(600), JBUI.scale(30)));

        JLabel packageHeader = new JLabel("Risk");
        packageHeader.setFont(JBUI.Fonts.label(12).asBold());
        packageHeader.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        riskHeaderPanel.add(packageHeader);
        columnHeaderPanel.add(riskHeaderPanel);

        // First half of flexible space - between Risk and Last Updated
        columnHeaderPanel.add(Box.createHorizontalGlue());

        // 3) Last Updated column header - fixed size with center alignment
        JPanel updatedHeaderPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        updatedHeaderPanel.setOpaque(false);
        updatedHeaderPanel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(30)));
        updatedHeaderPanel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(30)));
        updatedHeaderPanel.setMaximumSize(new Dimension(JBUI.scale(160), JBUI.scale(30)));

        JLabel updatedHeader = new JLabel("Last Updated");
        updatedHeader.setFont(JBUI.Fonts.label(12).asBold());
        updatedHeader.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        updatedHeader.setHorizontalAlignment(SwingConstants.CENTER);
        updatedHeaderPanel.add(updatedHeader);
        columnHeaderPanel.add(updatedHeaderPanel);

        // Second half of flexible space - between Last Updated and Actions
        columnHeaderPanel.add(Box.createHorizontalGlue());

        // 4) Actions column header - far right (no title)
        JPanel actionsHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionsHeaderPanel.setOpaque(false);
        actionsHeaderPanel.setPreferredSize(new Dimension(JBUI.scale(100), JBUI.scale(30)));
        actionsHeaderPanel.setMinimumSize(new Dimension(JBUI.scale(80), JBUI.scale(30)));
        actionsHeaderPanel.setMaximumSize(new Dimension(JBUI.scale(120), JBUI.scale(30)));
        // No label added - empty actions column header
        columnHeaderPanel.add(actionsHeaderPanel);


        headerPanel.add(columnHeaderPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    /**
     * Refresh ignored entries and rebuild UI panels
     */
    private void refreshIgnoredEntries() {
        if (!new GlobalSettingsComponent().isValid()) {
            return; // Don't refresh if not authenticated
        }

        try {
            List<IgnoreEntry> entries = new IgnoreManager(project).getIgnoredEntries();
            allEntries = new java.util.ArrayList<>(entries);
            applyFiltersAndRefresh();
            updateTabTitle(entries.size());
        } catch (Exception e) {
            LOGGER.warn("Error loading ignored entries", e);
            allEntries.clear();
            displayFilteredEntries(java.util.List.of());
            updateTabTitle(0);
        }
    }

    /**
     * Toggle select all checkbox functionality
     */
    private void toggleSelectAll() {
        boolean selected = selectAllCheckbox.isSelected();
        for (IgnoredEntryPanel panel : entryPanels) {
            panel.setSelected(selected);
        }
    }

    /**
     * Update tab title with ignored count using IntelliJ's native styling
     */
    private void updateTabTitle(int count) {
        if (content != null) {
            if (count > 0) {
                // Use setDisplayName with count - IntelliJ will handle the styling
                content.setDisplayName(DevAssistConstants.IGNORED_FINDINGS_TAB + " " + count);
            } else {
                content.setDisplayName(DevAssistConstants.IGNORED_FINDINGS_TAB);
            }
        }
    }

    @Override
    public void dispose() {
        // Cleanup resources if needed
    }

    /**
     * Individual ignored entry panel presented in a row-like layout
     */
    private static class IgnoredEntryPanel extends JPanel {
        private final IgnoreEntry entry;
        private final Project project;
        private final JCheckBox selectCheckBox;

        public IgnoredEntryPanel(IgnoreEntry entry, Project project) {
            this.entry = entry;
            this.project = project;

            // Use BorderLayout with fixed-width columns to prevent misalignment
            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(8, 12));
            setBackground(JBUI.CurrentTheme.ToolWindow.background());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(96)));

            // Create main panel with fixed column structure
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.X_AXIS));
            mainPanel.setOpaque(false);

            // 1) Checkbox column - fixed width
            JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            checkboxPanel.setOpaque(false);
            checkboxPanel.setPreferredSize(new Dimension(JBUI.scale(50), JBUI.scale(80)));
            checkboxPanel.setMinimumSize(new Dimension(JBUI.scale(50), JBUI.scale(80)));
            checkboxPanel.setMaximumSize(new Dimension(JBUI.scale(50), JBUI.scale(96)));

            selectCheckBox = new JCheckBox();
            selectCheckBox.setOpaque(false);
            checkboxPanel.add(selectCheckBox);
            mainPanel.add(checkboxPanel);

            // Add space between checkbox and risk column
            mainPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(12), 0)));

            // 2) Risk column - takes reasonable space with strict constraints
            JPanel riskPanel = buildRiskPanel();
            riskPanel.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(60)));
            riskPanel.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(80)));
            riskPanel.setMaximumSize(new Dimension(JBUI.scale(600), JBUI.scale(96)));

            // Wrap in a container to enforce size constraints strictly
            JPanel riskContainer = new JPanel(new BorderLayout());
            riskContainer.setOpaque(false);
            riskContainer.setMinimumSize(new Dimension(JBUI.scale(400), JBUI.scale(60)));
            riskContainer.setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(80)));
            riskContainer.setMaximumSize(new Dimension(JBUI.scale(600), JBUI.scale(96)));
            riskContainer.add(riskPanel, BorderLayout.CENTER);

            mainPanel.add(riskContainer);

            // First half of flexible space - between Risk and Last Updated
            mainPanel.add(Box.createHorizontalGlue());

            // 3) Last Updated column - fixed size with center alignment
            JPanel updatedPanel = new JPanel(new BorderLayout());
            updatedPanel.setOpaque(false);
            updatedPanel.setPreferredSize(new Dimension(JBUI.scale(140), JBUI.scale(80)));
            updatedPanel.setMinimumSize(new Dimension(JBUI.scale(120), JBUI.scale(80)));
            updatedPanel.setMaximumSize(new Dimension(JBUI.scale(160), JBUI.scale(96)));

            JLabel updatedLabel = new JLabel(getLastUpdatedText(entry.dateAdded));
            updatedLabel.setFont(JBUI.Fonts.label(12));
            updatedLabel.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            updatedLabel.setHorizontalAlignment(SwingConstants.CENTER);
            updatedPanel.add(updatedLabel, BorderLayout.CENTER);
            mainPanel.add(updatedPanel);

            // Second half of flexible space - between Last Updated and Actions
            mainPanel.add(Box.createHorizontalGlue());

            // 4) Actions column - far right position
            JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actionsPanel.setOpaque(false);
            actionsPanel.setPreferredSize(new Dimension(JBUI.scale(100), JBUI.scale(80)));
            actionsPanel.setMinimumSize(new Dimension(JBUI.scale(80), JBUI.scale(80)));
            actionsPanel.setMaximumSize(new Dimension(JBUI.scale(120), JBUI.scale(96)));

            JButton reviveButton = new JButton("Revive");
            reviveButton.setEnabled(true); // UI only for now, no backend logic
            reviveButton.setPreferredSize(new Dimension(JBUI.scale(70), JBUI.scale(28)));
            reviveButton.setFont(JBUI.Fonts.label(11));
            reviveButton.addActionListener(ev -> {
                // Placeholder: no-op for now, backend not required per user request
                LOGGER.info("Revive clicked for: " + (entry.packageName != null ? entry.packageName : "unknown"));
            });
            actionsPanel.add(reviveButton);
            mainPanel.add(actionsPanel);


            add(mainPanel, BorderLayout.CENTER);


            // Hover effect
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

        private JPanel buildRiskPanel() {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            // Ensure the panel respects its maximum size constraints
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            // First line: large vulnerability icon + severity icon + display name
            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(0)));
            topLine.setOpaque(false);
            topLine.setMaximumSize(new Dimension(JBUI.scale(580), JBUI.scale(50))); // Constrain width

            // Large vulnerability card icon (44x44)
            JLabel vulnerabilityIcon = buildVulnerabilityCardIcon(entry.type, entry.severity);
            topLine.add(vulnerabilityIcon);

            // Small severity icon (16x16)
            JLabel severityIcon = buildSeverityIcon(entry.severity);
            topLine.add(severityIcon);

            // Display name with truncation to prevent overflow
            String displayName = formatDisplayName();
            if (displayName.length() > 45) { // Reduced from 50 to 45 for better constraint
                displayName = displayName.substring(0, 42) + "...";
            }
            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setFont(JBUI.Fonts.label(13).asBold());
            nameLabel.setToolTipText(formatDisplayName()); // Show full name on hover
            topLine.add(nameLabel);

            panel.add(topLine);

            // Second line: engine chip + file buttons
            JPanel filesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)));
            filesRow.setOpaque(false);
            filesRow.setMaximumSize(new Dimension(JBUI.scale(580), JBUI.scale(40))); // Constrain width

            // Engine chip icon
            JLabel engineChip = buildEngineChipIcon(entry.type);
            filesRow.add(engineChip);

            // Add file buttons (visible + hidden with expand)
            JPanel fileButtons = buildFileButtons();
            filesRow.add(fileButtons);

            panel.add(filesRow);
            return panel;
        }

        private JLabel buildVulnerabilityCardIcon(ScanEngine type, String severity) {
            try {
                String iconName = getCardIconName(type, severity);
                String iconPath = "/icons/devassist/ignored_card/" + iconName;

                // Try to load the icon using IconLoader
                Icon icon = IconLoader.getIcon(iconPath, CxIgnoredFindings.class);
                if (icon != null) {
                    return new JLabel(icon);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to load vulnerability card icon: " + e.getMessage());
            }

            // Fallback to a simple colored square
            JLabel fallback = new JLabel();
            fallback.setPreferredSize(new Dimension(JBUI.scale(44), JBUI.scale(44)));
            fallback.setOpaque(true);
            fallback.setBackground(getSeverityColor(severity));
            return fallback;
        }

        private JLabel buildSeverityIcon(String severity) {
            try {
                String severityName = severity != null ? severity.toLowerCase() : "unknown";
                String iconPath = "/icons/devassist/severity_16/" + severityName + ".svg";

                Icon icon = IconLoader.getIcon(iconPath, CxIgnoredFindings.class);
                if (icon != null) {
                    return new JLabel(icon);
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to load severity icon: " + e.getMessage());
            }

            // Fallback to a small colored circle
            JLabel fallback = new JLabel("●");
            fallback.setForeground(getSeverityColor(severity));
            fallback.setFont(JBUI.Fonts.label(16));
            return fallback;
        }

        private JLabel buildEngineChipIcon(ScanEngine type) {
            try {
                String chipName = getEngineChipName(type);
                if (chipName != null) {
                    String iconPath = "/icons/devassist/ignored/" + chipName;
                    Icon icon = IconLoader.getIcon(iconPath, CxIgnoredFindings.class);
                    if (icon != null) {
                        return new JLabel(icon);
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to load engine chip icon: " + e.getMessage());
            }

            // Fallback to text
            String text = type != null ? type.name() : "?";
            JBLabel label = new JBLabel(text);
            label.setFont(JBUI.Fonts.miniFont());
            label.setForeground(JBColor.foreground());
            return label;
        }

        private String getCardIconName(ScanEngine type, String severity) {
            String severityLower = severity != null ? severity.toLowerCase() : "unknown";
            String typePrefix;

            switch (type) {
                case OSS:
                    typePrefix = "package";
                    break;
                case SECRETS:
                    typePrefix = "secret";
                    break;
                case IAC:
                case ASCA:
                    typePrefix = "vulnerability";
                    break;
                case CONTAINERS:
                    typePrefix = "containers";
                    break;
                default:
                    typePrefix = "vulnerability";
            }

            return "card icon-" + typePrefix + "-" + severityLower + "-default.svg";
        }

        private String getEngineChipName(ScanEngine type) {
            switch (type) {
                case OSS:
                    return "engine-chip-sca.svg";
                case SECRETS:
                    return "engine-chip-secrets.svg";
                case IAC:
                    return "engine-chip-iac.svg";
                case ASCA:
                    return "engine-chip-sast.svg";
                case CONTAINERS:
                    return "engine-chip-containers.svg";
                default:
                    return null;
            }
        }

        private JPanel buildFileButtons() {
            JPanel container = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(0)));
            container.setOpaque(false);

            if (entry.files == null || entry.files.isEmpty()) {
                JLabel none = new JLabel("No files");
                none.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
                container.add(none);
                return container;
            }

            // Only active files
            java.util.List<IgnoreEntry.FileReference> activeFiles = new java.util.ArrayList<>();
            for (IgnoreEntry.FileReference f : entry.files) {
                if (f != null && f.active) activeFiles.add(f);
            }

            int maxVisible = 1;
            java.util.List<IgnoreEntry.FileReference> visible = activeFiles.subList(0, Math.min(maxVisible, activeFiles.size()));
            java.util.List<IgnoreEntry.FileReference> hidden = activeFiles.size() > maxVisible
                    ? activeFiles.subList(maxVisible, activeFiles.size())
                    : java.util.Collections.emptyList();

            for (IgnoreEntry.FileReference f : visible) {
                JButton btn = new JButton(shortFileLabel(f));
                btn.setToolTipText(f.path + (f.line != null ? ":" + f.line : ""));
                btn.addActionListener(ev -> navigateTo(f));
                container.add(btn);
            }

            int remaining = hidden.size();
            if (remaining > 0) {
                JButton expand = new JButton("and " + remaining + " more files");
                JPanel hiddenPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(0)));
                hiddenPanel.setOpaque(false);
                hiddenPanel.setVisible(false);

                for (IgnoreEntry.FileReference f : hidden) {
                    JButton btn = new JButton(shortFileLabel(f));
                    btn.setToolTipText(f.path + (f.line != null ? ":" + f.line : ""));
                    btn.addActionListener(ev -> navigateTo(f));
                    hiddenPanel.add(btn);
                }

                expand.addActionListener(ev -> {
                    hiddenPanel.setVisible(!hiddenPanel.isVisible());
                    expand.setText(hiddenPanel.isVisible() ? "collapse" : "and " + remaining + " more files");
                    container.revalidate();
                    container.repaint();
                });

                container.add(expand);
                container.add(hiddenPanel);
            }

            return container;
        }

        private void navigateTo(IgnoreEntry.FileReference file) {
            if (file == null || file.path == null) return;
            VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(file.path);
            if (vFile != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(vFile, true);
                // Optionally navigate to line if supported; kept simple for now
            }
        }

        private String shortFileLabel(IgnoreEntry.FileReference f) {
            if (f == null || f.path == null) return "file";
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(f.path);
                String name = p.getFileName() != null ? p.getFileName().toString() : f.path;
                return f.line != null ? name + ":" + f.line : name;
            } catch (Exception ex) {
                return f.line != null ? f.path + ":" + f.line : f.path;
            }
        }

        private String formatDisplayName() {
            // Format based on scanner type using rules similar to VSCode
            String keyName = entry.packageName != null ? entry.packageName : "Unknown";
            switch (entry.type) {
                case OSS:
                    // npm:lodash:4.17.20 => npm@lodash@4.17.20
                    String mgr = entry.packageManager != null ? entry.packageManager : "pkg";
                    String ver = entry.packageVersion != null ? entry.packageVersion : "";
                    return mgr + "@" + keyName + (ver.isEmpty() ? "" : "@" + ver);
                case SECRETS:
                    // "AWS Access Key:secret123:file.js" => "AWS Access Key" (we have PackageName)
                    return keyName;
                case IAC:
                    // "Rule Name:sim123:terraform.tf" => "Rule Name"
                    return keyName;
                case ASCA:
                    // "Rule Name:123:file.py" => "Rule Name" (we have title or packageName)
                    return entry.title != null ? entry.title : keyName;
                case CONTAINERS:
                    // "nginx:1.20:dockerfile" => "nginx@1.20"
                    String tag = entry.imageTag != null ? entry.imageTag : entry.packageVersion;
                    return keyName + (tag != null && !tag.isEmpty() ? "@" + tag : "");
                default:
                    return keyName;
            }
        }

        private String getLastUpdatedText(String isoDate) {
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

        private String safePackageKey() {
            return (entry.packageManager != null ? entry.packageManager + ":" : "") +
                    (entry.packageName != null ? entry.packageName : "unknown") +
                    (entry.packageVersion != null ? ":" + entry.packageVersion : "");
        }

        private Color getSeverityColor(String severity) {
            String upperSeverity = severity != null ? severity.toUpperCase() : "";
            switch (upperSeverity) {
                case "CRITICAL":
                case "MALICIOUS":
                    return new JBColor(0xD32F2F, 0xF44336);
                case "HIGH":
                    return new JBColor(0xF57C00, 0xFF9800);
                case "MEDIUM":
                    return new JBColor(0xFBC02D, 0xFFEB3B);
                case "LOW":
                    return new JBColor(0x388E3C, 0x4CAF50);
                default:
                    return new JBColor(0x757575, 0x9E9E9E);
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
