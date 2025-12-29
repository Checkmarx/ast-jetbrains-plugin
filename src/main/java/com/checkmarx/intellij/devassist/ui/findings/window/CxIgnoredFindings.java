package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class CxIgnoredFindings extends SimpleToolWindowPanel implements Disposable {
    private static final Logger LOGGER = Utils.getLogger(CxIgnoredFindings.class);

    private final Project project;
    private final JPanel ignoredListPanel;
    private final Content content;
    private IgnoreManager ignoreManager;

    public CxIgnoredFindings(Project project, Content content) {
        super(false, true);
        this.project = project;
        this.content = content;
        this.ignoreManager = IgnoreManager.getInstance(project);

        ignoredListPanel = new JPanel();
        ignoredListPanel.setLayout(new BoxLayout(ignoredListPanel, BoxLayout.Y_AXIS));
        ignoredListPanel.setBackground(JBUI.CurrentTheme.ToolWindow.background());

        JBScrollPane scrollPane = new JBScrollPane(ignoredListPanel);
        scrollPane.setBorder(JBUI.Borders.empty());
        setContent(scrollPane);

        refreshIgnoredEntries();

        // Auto-refresh every 5 seconds
        Timer refreshTimer = new Timer(5000, e -> refreshIgnoredEntries());
        refreshTimer.start();

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
     * Refresh ignored entries and rebuild UI panels
     */
    private void refreshIgnoredEntries() {
        ignoredListPanel.removeAll();

        List<IgnoreEntry> ignoredEntries = ignoreManager.getIgnoredEntries();
        if (ignoredEntries.isEmpty()) {
            JLabel emptyLabel = new JLabel("No ignored vulnerabilities");
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setBorder(new EmptyBorder(40, 0, 40, 0));
            ignoredListPanel.add(emptyLabel);
        } else {
            for (IgnoreEntry entry : ignoredEntries) {
                IgnoredEntryPanel panel = new IgnoredEntryPanel(entry, project);
                ignoredListPanel.add(panel);
            }
        }

        ignoredListPanel.revalidate();
        ignoredListPanel.repaint();
        updateTabTitle(ignoredEntries.size());
    }

    private void updateTabTitle(int count) {
        if (count > 0) {
            content.setDisplayName(Constants.RealTimeConstants.IGNORED_FINDINGS_TAB + " " + count );
        } else {
            content.setDisplayName(Constants.RealTimeConstants.IGNORED_FINDINGS_TAB);
        }
    }

    @Override
    public void dispose() {
        // Timer cleanup handled by parent
    }

    /**
     * Individual ignored entry panel (horizontal card layout)
     */
    private static class IgnoredEntryPanel extends JPanel {
        private final IgnoreEntry entry;
        private final Project project;
        private final JCheckBox selectCheckBox;
        private final JLabel titleLabel;
        private final JLabel detailsPanel;
        private final JLabel filesLabel;

        public IgnoredEntryPanel(IgnoreEntry entry, Project project) {
            this.entry = entry;
            this.project = project;
            setLayout(new BorderLayout());
            setBorder(JBUI.Borders.empty(12, 12));
            setBackground(JBUI.CurrentTheme.ToolWindow.background());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(80)));

            // Selection checkbox (for multi-select)
            selectCheckBox = new JCheckBox();
            selectCheckBox.setOpaque(false);

            // Title row
            JPanel titlePanel = new JPanel(new BorderLayout());
            titleLabel = new JLabel(entry.packageName != null ? entry.packageName : "Unknown Package");
            titleLabel.setFont(JBUI.Fonts.label(14).asBold());

            JPanel severityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            JLabel severityBadge = new JLabel(entry.severity != null ? entry.severity.toUpperCase() : "UNKNOWN");
            severityBadge.setOpaque(true);
            severityBadge.setBorder(JBUI.Borders.empty(8, 8));
            severityBadge.setBackground(getSeverityColor(entry.severity));
            severityBadge.setForeground(Color.WHITE);
            severityBadge.setFont(JBUI.Fonts.miniFont().asBold());
            severityPanel.add(severityBadge);

            titlePanel.add(titleLabel, BorderLayout.WEST);
            titlePanel.add(severityPanel, BorderLayout.EAST);

            // Details row
            detailsPanel = new JLabel(getDetailsText());
            detailsPanel.setFont(JBUI.Fonts.label(12));

            // Files row
            filesLabel = new JLabel(getFilesText());
            filesLabel.setFont(JBUI.Fonts.label(11));

            // Main content panel
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);
            contentPanel.add(titlePanel);
            contentPanel.add(detailsPanel);
            contentPanel.add(filesLabel);

            add(selectCheckBox, BorderLayout.WEST);
            add(contentPanel, BorderLayout.CENTER);

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

            // Right-click popup
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) showPopup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) showPopup(e);
                }
            });
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


        private String getDetailsText() {
            return String.format("%s • %s %s",
                    entry.packageManager != null ? entry.packageManager.toUpperCase() : "Unknown",
                    entry.type != null ? entry.type : "Unknown",
                    entry.packageVersion != null ? entry.packageVersion : "");
        }

        private String getFilesText() {
            if (entry.files != null && !entry.files.isEmpty()) {
                return String.format("%d file(s): %s",
                        entry.files.size(),
                        entry.files.get(0).path);
            }
            return "No files";
        }

        private void showPopup(MouseEvent e) {
            JPopupMenu popup = new JPopupMenu();

            JMenuItem unignoreItem = new JMenuItem("Unignore this vulnerability");
            unignoreItem.addActionListener(ev -> {
                // TODO: Implement unignore logic
                LOGGER.info("Unignore: " + entry.packageName);
            });
            popup.add(unignoreItem);

            JMenuItem navigateItem = new JMenuItem("Navigate to file");
            navigateItem.addActionListener(ev -> {
                if (!entry.files.isEmpty()) {
                    String filePath = entry.files.get(0).path;
                    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
                    if (file != null) {
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                                .openFile(file, true);
                    }
                }
            });
            popup.add(navigateItem);

            popup.show(this, e.getX(), e.getY());
        }

        public boolean isSelected() {
            return selectCheckBox.isSelected();
        }
    }
}

