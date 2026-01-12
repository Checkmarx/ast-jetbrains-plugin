package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.*;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.ui.actions.VulnerabilityFilterBaseAction;
import com.checkmarx.intellij.devassist.ui.actions.VulnerabilityFilterState;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable;
import com.checkmarx.intellij.tool.window.DevAssistPromotionalPanel;
import com.checkmarx.intellij.tool.window.FindingsPromotionalPanel;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.checkmarx.intellij.util.SeverityLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.QUICK_FIX;


/**
 * Handles drawing of the Checkmarx vulnerability tool window.
 * Extends {@link SimpleToolWindowPanel} to provide a panel with toolbar and content area.
 * Implements {@link Disposable} for cleanup toolbar actions.
 * Manages a tree view of vulnerabilities with filtering and navigation capabilities.
 * Initializes icons for different vulnerability severities.
 * Subscribes to settings changes and problem updates to refresh the UI accordingly.
 * Uses a timer to periodically update the tab title with the current problem count.
 * Refactored to have separate drawAuthPanel() and drawMainPanel() following pattern in CxToolWindowPanel.
 */
public class CxFindingsWindow extends SimpleToolWindowPanel implements Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxFindingsWindow.class);

    private final Project project;
    private final SimpleTree tree;
    private final DefaultMutableTreeNode rootNode;
    private static Map<String, Icon> vulnerabilityCountToIcon;
    private static Map<String, Icon> vulnerabilityToIcon;
    private static Set<String> expandedPathsSet = new HashSet<>();
    private final Content content;
    private final Timer timer;

    private final RemediationManager remediationManager = new RemediationManager();

    private boolean treeInitialized = false;

    public CxFindingsWindow(Project project, Content content) {
        super(false, true);
        this.project = project;
        this.tree = new SimpleTree();
        this.rootNode = new DefaultMutableTreeNode();
        this.content = content;

        // Setup initial UI based on authentication and license status
        // License Matrix for Findings tab:
        // - NOT authenticated: Show auth panel
        // - Authenticated + (One Assist OR Dev Assist): Show split view (findings + promotional)
        // - Authenticated + no license: Show full-screen promotional panel
        Runnable settingsCheckRunnable = () -> {
            try {
                if (!new GlobalSettingsComponent().isValid()) {
                    LOGGER.info("CxFindingsWindow: Not authenticated - showing auth panel");
                    drawAuthPanel();
                } else {
                    // Authenticated - check licenses
                    boolean hasOneAssist = checkOneAssistLicense();
                    boolean hasDevAssist = checkDevAssistLicense();
                    boolean hasAnyLicense = hasOneAssist || hasDevAssist;

                    LOGGER.info("CxFindingsWindow: Authenticated, hasOneAssist=" + hasOneAssist
                            + ", hasDevAssist=" + hasDevAssist + ", hasAnyLicense=" + hasAnyLicense);

                    if (hasAnyLicense) {
                        // Show split view: findings on left, promotional on right
                        drawSplitPanel();
                    } else {
                        // No license - show full-screen promotional panel
                        drawPromotionalPanel();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("CxFindingsWindow: Error during settings check, showing auth panel as fallback", e);
                drawAuthPanel();
            }
        };

        // Timer for updating tab title count - initialize early to avoid final field issues
        timer = new Timer(1000, e -> updateTabTitle());

        try {
            project.getMessageBus().connect(this)
                    .subscribe(VulnerabilityFilterBaseAction.TOPIC,
                            (VulnerabilityFilterBaseAction.VulnerabilityFilterChanged) () -> ApplicationManager.getApplication().invokeLater(this::triggerRefreshTree));

            ApplicationManager.getApplication().getMessageBus()
                    .connect(this)
                    .subscribe(SettingsListener.SETTINGS_APPLIED, new SettingsListener() {
                        @Override
                        public void settingsApplied() {
                            settingsCheckRunnable.run();
                        }
                    });

            settingsCheckRunnable.run();

            LOGGER.debug("Initiated the custom problem window for project: " + project.getName());

            timer.start();
            Disposer.register(this, () -> timer.stop());
        } catch (Exception e) {
            LOGGER.error("CxFindingsWindow: Error during initialization", e);
            // Show auth panel as fallback
            drawAuthPanel();
        }
    }

    /**
     * Initialize tree components (icons, model, renderer, listeners).
     * Called lazily only when the tree is actually needed (main panel or split panel).
     */

    private void initializeTreeIfNeeded() {
        if (treeInitialized) {
            return;
        }
        treeInitialized = true;

        // Initialize icons for rendering
        initVulnerabilityCountIcons();
        initVulnerabilityIcons();

        // Setup tree model and renderer
        tree.setModel(new DefaultTreeModel(rootNode));
        tree.setCellRenderer(new IssueTreeRenderer(tree, vulnerabilityToIcon, vulnerabilityCountToIcon));
        tree.setRootVisible(false);

        // Add mouse listeners for navigation and popup menu
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1)
                    navigateToSelectedIssue();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleRightClick(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleRightClick(e);
            }
        });

        // Trigger initial refresh with existing scan results if any (on EDT)
        SwingUtilities.invokeLater(() -> {
            Map<String, List<ScanIssue>> existingIssues = ProblemHolderService.getInstance(project).getAllIssues();
            if (!existingIssues.isEmpty()) {
                triggerRefreshTree();
            }
        });

        // Subscribe to scan issue updates to refresh tree automatically
        project.getMessageBus().connect(this)
                .subscribe(ProblemHolderService.ISSUE_TOPIC, new ProblemHolderService.IssueListener() {
                    @Override
                    public void onIssuesUpdated(Map<String, List<ScanIssue>> issues) {
                        ApplicationManager.getApplication().invokeLater(() -> triggerRefreshTree());
                    }
                });
    }

    /**
     * Draw the authentication panel prompting the user to configure settings.
     *
     */
    private void drawAuthPanel() {
        LOGGER.info("drawAuthPanel: Drawing authentication panel");

        // Remove toolbar when showing auth panel (only if one exists)
        if (getToolbar() != null) {
            setToolbar(null);
        }

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
        LOGGER.info("drawAuthPanel: Auth panel set as content");
    }

    /**
     * Draw the main panel with toolbar and tree inside a scroll pane.
     * This is the findings content panel (used as part of split view).
     */
    private void drawMainPanel() {
        LOGGER.info("drawMainPanel: Drawing main panel with tree");

        // Initialize tree components if not already done
        initializeTreeIfNeeded();

        // Create and set toolbar
        ActionToolbar toolbar = createActionToolbar();
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        // Add tree inside scroll pane
        JBScrollPane scrollPane = new JBScrollPane(tree);
        setContent(scrollPane);

        revalidate();
        repaint();
        LOGGER.info("drawMainPanel: Main panel set as content");
    }

    /**
     * Draw a split view with findings panel on left and promotional panel on right.
     * Shown when authenticated and at least one license (One Assist OR Dev Assist) is active.
     */
    private void drawSplitPanel() {
        LOGGER.info("drawSplitPanel: Drawing split panel");

        // Initialize tree components if not already done
        initializeTreeIfNeeded();

        // Create and set toolbar on the SimpleToolWindowPanel
        ActionToolbar toolbar = createActionToolbar();
        toolbar.setTargetComponent(this);
        setToolbar(toolbar.getComponent());

        // Create findings panel with tree in scroll pane
        JBScrollPane scrollPane = new JBScrollPane(tree);

        // Create promotional panel for findings
        FindingsPromotionalPanel promotionalPanel = new FindingsPromotionalPanel();

        // Create splitter with vertical divider (false = left/right layout)
        JBSplitter splitter = new JBSplitter(false, 0.5f);
        splitter.setFirstComponent(scrollPane);
        splitter.setSecondComponent(promotionalPanel);
        splitter.setDividerWidth(3);

        setContent(splitter);

        revalidate();
        repaint();
        LOGGER.info("drawSplitPanel: Split panel set as content");

        // Trigger refresh to populate the tree with findings
        triggerRefreshTree();
    }

    /**
     * Draw a full-screen promotional panel for Dev Assist.
     * Shown when authenticated but neither One Assist nor Dev Assist license is active.
     */
    private void drawPromotionalPanel() {
        LOGGER.info("drawPromotionalPanel: Drawing full-screen promotional panel");

        // Remove toolbar when showing full-screen promotional panel (only if one exists)
        if (getToolbar() != null) {
            setToolbar(null);
        }

        DevAssistPromotionalPanel promotionalPanel = new DevAssistPromotionalPanel();
        setContent(promotionalPanel);

        revalidate();
        repaint();
        LOGGER.info("drawPromotionalPanel: Promotional panel set as content");
    }

    /**
     * Check if the current tenant has Checkmarx One Assist license.
     *
     * @return true if One Assist license is enabled, false otherwise
     */
    private boolean checkOneAssistLicense() {
        try {
            return TenantSetting.isOneAssistEnabled();
        } catch (Exception e) {
            LOGGER.warn("Failed to check One Assist license status", e);
            return false;
        }
    }

    /**
     * Check if the current tenant has Checkmarx Dev Assist license.
     *
     * @return true if Dev Assist license is enabled, false otherwise
     */
    private boolean checkDevAssistLicense() {
        try {
            return TenantSetting.isDevAssistEnabled();
        } catch (Exception e) {
            LOGGER.warn("Failed to check Dev Assist license status", e);
            return false;
        }
    }

    /**
     * Retrieve issues, apply filtering, and refresh the UI tree.
     */
    private void triggerRefreshTree() {
        Map<String, List<ScanIssue>> allIssues = ProblemHolderService.getInstance(project).getAllIssues();
        if (allIssues.isEmpty()) {
            return;
        }

        Set<Filterable> activeFilters = VulnerabilityFilterState.getInstance().getFilters();
        Map<String, List<ScanIssue>> filteredIssues = new HashMap<>();

        for (Map.Entry<String, List<ScanIssue>> entry : allIssues.entrySet()) {
            List<ScanIssue> filteredList = entry.getValue().stream()
                    .filter(issue -> activeFilters.stream()
                            .anyMatch(f -> f.getFilterValue().equalsIgnoreCase(issue.getSeverity())))
                    .collect(Collectors.toList());

            if (!filteredList.isEmpty()) {
                filteredIssues.put(entry.getKey(), filteredList);
            }
        }
        refreshTree(filteredIssues);
    }

    public void refreshTree(Map<String, List<ScanIssue>> issues) {
        int rowCount = tree.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            TreePath path = tree.getPathForRow(i);
            if (path != null && tree.isExpanded(path)) {
                Object lastNode = path.getLastPathComponent();
                if (lastNode instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastNode;
                    Object userObject = node.getUserObject();
                    if (userObject instanceof FileNodeLabel) {
                        expandedPathsSet.add(((FileNodeLabel) userObject).filePath);
                    }
                }
            }
        }
        // Clear and rebuild the tree
        rootNode.removeAllChildren();
        for (Map.Entry<String, List<ScanIssue>> entry : issues.entrySet()) {
            String filePath = entry.getKey();
            String fileName = getSecureFileName(filePath);
            List<ScanIssue> scanDetails = entry.getValue();

            // Filtered problems (excluding "ok" and "unknown" and "ignored" severity)
            List<ScanIssue> filteredScanDetails = scanDetails.stream()
                    .filter(detail -> {
                        return DevAssistUtils.isProblem(detail.getSeverity());
                    })
                    .collect(Collectors.toList());
            if (!filteredScanDetails.isEmpty())
                ApplicationManager.getApplication().runReadAction(() ->
                    createFileNode(filePath, filteredScanDetails, fileName));
        }
        ((DefaultTreeModel) tree.getModel()).reload();
        expandNodesByFilePath();
    }

    /**
     * Creating file node with its issues as child nodes.
     */
    private void createFileNode(String filePath, List<ScanIssue> filteredScanDetails, String fileName) {
        try {
            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
            Icon icon = virtualFile != null ? virtualFile.getFileType().getIcon() : null;
            PsiFile psiFile = virtualFile != null ? PsiManager.getInstance(project).findFile(virtualFile) : null;
            if (psiFile != null) {
                icon = psiFile.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
            }
            // Count issues by severity and sort them based on severity order
            Map<String, Long> severityCounts = filteredScanDetails.stream()
                    .collect(Collectors.groupingBy(ScanIssue::getSeverity, Collectors.counting()));

            severityCounts = getSeverityList().stream().filter(severityCounts::containsKey)
                    .collect(Collectors.toMap(severity -> severity, severityCounts::get,
                            (a, b) -> a, LinkedHashMap::new));

            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(new FileNodeLabel(fileName, filePath, severityCounts, icon));

            for (ScanIssue detail : filteredScanDetails) {
                fileNode.add(new DefaultMutableTreeNode(new ScanDetailWithPath(detail, filePath)));
            }
            rootNode.add(fileNode);
        } catch (Exception e) {
            LOGGER.warn("Exception occurred! Failed to create file node for file: " + filePath, e);
        }
    }

    /**
     * Expand nodes by file path after reload.
     */
    private void expandNodesByFilePath() {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < tree.getRowCount(); i++) {
                TreePath path = tree.getPathForRow(i);
                if (path != null) {
                    Object lastNode = path.getLastPathComponent();
                    if (lastNode instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastNode;
                        Object userObject = node.getUserObject();
                        if (userObject instanceof FileNodeLabel &&
                                expandedPathsSet.contains(((FileNodeLabel) userObject).filePath)) {
                            tree.expandPath(path);
                        }
                    }
                }
            }
        });
    }

    private void navigateToSelectedIssue() {
        Object selected = tree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode))
            return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selected;
        Object userObj = node.getUserObject();
        if (!(userObj instanceof ScanDetailWithPath))
            return;

        ScanDetailWithPath detailWithPath = (ScanDetailWithPath) userObj;
        ScanIssue detail = detailWithPath.detail;
        String filePath = detailWithPath.filePath;

        if (detail.getLocations() != null && !detail.getLocations().isEmpty()) {
            Location targetLoc = detail.getLocations().get(0);

            int lineNumber = targetLoc.getLine();

            VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (virtualFile == null)
                return;

            FileEditorManager editorManager = FileEditorManager.getInstance(project);
            editorManager.openFile(virtualFile, true);
            Editor editor = editorManager.getSelectedTextEditor();
            if (editor != null) {
                Document document = editor.getDocument();
                LogicalPosition logicalPosition = new LogicalPosition(lineNumber - 1, 0);
                editor.getCaretModel().moveToLogicalPosition(logicalPosition);
                editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.CENTER);

            }
        }
    }

    private void handleRightClick(MouseEvent e) {
        if (!e.isPopupTrigger())
            return;
        int row = tree.getClosestRowForLocation(e.getX(), e.getY());
        tree.setSelectionRow(row);
        Object selected = tree.getLastSelectedPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode))
            return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selected;
        Object userObj = node.getUserObject();
        if (!(userObj instanceof ScanDetailWithPath))
            return;

        ScanIssue detail = ((ScanDetailWithPath) userObj).detail;
        JPopupMenu popup = createPopupMenu(detail);
        popup.show(tree, e.getX(), e.getY());
    }

    @Override
    public void dispose() {
        // Cleanup if needed
    }

    public static class ScanDetailWithPath {
        public final ScanIssue detail;
        public final String filePath;

        public ScanDetailWithPath(ScanIssue detail, String filePath) {
            this.detail = detail;
            this.filePath = filePath;
        }
    }

    private JPopupMenu createPopupMenu(ScanIssue detail) {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem fixWithCxOneAssist = new JMenuItem(DevAssistConstants.FIX_WITH_CXONE_ASSIST);
        fixWithCxOneAssist.addActionListener(ev -> {
            TelemetryService.logFixWithCxOneAssistAction(detail);
            remediationManager.fixWithCxOneAssist(project, detail, QUICK_FIX);
        });
        fixWithCxOneAssist.setIcon(CxIcons.STAR_ACTION);
        popup.add(fixWithCxOneAssist);

        JMenuItem copyDescription = new JMenuItem(DevAssistConstants.VIEW_DETAILS_FIX_NAME);
        copyDescription.addActionListener(ev -> {
            TelemetryService.logViewDetailsAction(detail);
            remediationManager.viewDetails(project, detail, QUICK_FIX);
        });
        copyDescription.setIcon(CxIcons.STAR_ACTION);
        popup.add(copyDescription);

        JMenuItem ignoreOption = new JMenuItem(DevAssistConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME);
        ignoreOption.setIcon(CxIcons.STAR_ACTION);
        ignoreOption.addActionListener(ev -> new IgnoreManager(project).addIgnoredEntry(detail, QUICK_FIX));
        popup.add(ignoreOption);

        // Only show "Ignore all of this type" for container and oss
        if (ScanEngine.CONTAINERS.toString().equalsIgnoreCase(detail.getScanEngine().toString()) || ScanEngine.OSS.toString().equalsIgnoreCase(detail.getScanEngine().toString())) {
            JMenuItem ignoreAllOption = new JMenuItem(DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME);
            ignoreAllOption.setIcon(CxIcons.STAR_ACTION);
            ignoreAllOption.addActionListener(ev -> new IgnoreManager(project).addAllIgnoredEntry(detail, QUICK_FIX));
            popup.add(ignoreAllOption);
        }
        popup.add(new JSeparator());

        JMenuItem copyFix = new JMenuItem("Copy");
        copyFix.addActionListener(ev -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Collections.singletonList(detail));
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(json), null);
            } catch (Exception e) {
                LOGGER.warn("Failed to copy fix details", e);
            }
        });
        popup.add(copyFix);

        JMenuItem copyMessage = new JMenuItem("Copy Message");
        copyMessage.addActionListener(ev -> {
            String message = detail.getSeverity() + "-risk package: " + detail.getTitle() + "@"
                    + detail.getPackageVersion();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(message), null);
        });
        popup.add(copyMessage);
        return popup;
    }

    private String getSecureFileName(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return Constants.UNKNOWN;
        }
        try {
            Path path = Paths.get(filePath).normalize();
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
            return path.toString();
        } catch (java.nio.file.InvalidPathException e) {
            return filePath.substring(Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1);
        }
    }

    public static class FileNodeLabel {
        public final String fileName;
        public final String filePath;
        public final Map<String, Long> problemCount;
        public final Icon icon;

        public FileNodeLabel(String fileName, String filePath, Map<String, Long> problemCount, Icon icon) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.problemCount = problemCount;
            this.icon = icon;
        }
    }

    public void updateTabTitle() {
        int count = getProblemCount();
        if (count > 0) {
            JBColor jbColor = new JBColor(Gray._10, Gray._190);
            String hexColor = "#" + Integer.toHexString(jbColor.getRGB()).substring(2);
            content.setDisplayName("<html>" + DevAssistConstants.DEVASSIST_TAB + " <span style='color:" + hexColor + "'>" + count + "</span></html>");
        } else {
            content.setDisplayName(DevAssistConstants.DEVASSIST_TAB);
        }
    }

    public int getProblemCount() {
        int count = 0;
        Enumeration children = rootNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) children.nextElement();
            count += fileNode.getChildCount(); // problems under each file node
        }
        return count;
    }

    @NotNull
    private ActionToolbar createActionToolbar() {
        ActionGroup originalXmlGroup =
                (ActionGroup) ActionManager.getInstance().getAction("VulnerabilityToolbarGroup");

        DefaultActionGroup newGroup = new DefaultActionGroup();
        DataContext dataContext = DataManager.getInstance().getDataContext(/* component or null */);
        AnActionEvent event = AnActionEvent.createFromDataContext(
                ActionPlaces.TOOLBAR,
                null,
                dataContext
        );
        for (AnAction a : originalXmlGroup.getChildren(event)) {
            newGroup.add(a);
        }

        // Add Expand/Collapse actions
        AnAction expandAll = ActionManager.getInstance().getAction("Checkmarx.ExpandAll");
        AnAction collapseAll = ActionManager.getInstance().getAction("Checkmarx.CollapseAll");

        newGroup.add(expandAll);
        newGroup.add(collapseAll);

        ActionToolbar toolbar = ActionManager.getInstance()
                .createActionToolbar(Constants.TOOL_WINDOW_ID, newGroup, false);

        toolbar.setTargetComponent(this);
        return toolbar;
    }

    private void initVulnerabilityIcons() {
        vulnerabilityToIcon = new HashMap<>();
        vulnerabilityToIcon.put(Constants.MALICIOUS_SEVERITY, CxIcons.Small.MALICIOUS);
        vulnerabilityToIcon.put(Constants.CRITICAL_SEVERITY, CxIcons.Small.CRITICAL);
        vulnerabilityToIcon.put(Constants.HIGH_SEVERITY, CxIcons.Small.HIGH);
        vulnerabilityToIcon.put(Constants.MEDIUM_SEVERITY, CxIcons.Small.MEDIUM);
        vulnerabilityToIcon.put(Constants.LOW_SEVERITY, CxIcons.Small.LOW);
    }

    private void initVulnerabilityCountIcons() {
        vulnerabilityCountToIcon = new HashMap<>();
        vulnerabilityCountToIcon.put(Constants.MALICIOUS_SEVERITY, CxIcons.Medium.MALICIOUS);
        vulnerabilityCountToIcon.put(Constants.CRITICAL_SEVERITY, CxIcons.Medium.CRITICAL);
        vulnerabilityCountToIcon.put(Constants.HIGH_SEVERITY, CxIcons.Medium.HIGH);
        vulnerabilityCountToIcon.put(Constants.MEDIUM_SEVERITY, CxIcons.Medium.MEDIUM);
        vulnerabilityCountToIcon.put(Constants.LOW_SEVERITY, CxIcons.Medium.LOW);
    }

    /**
     * Get a list of severity levels as strings in defined order.
     *
     * @return List<String> of severity levels
     */
    private List<String> getSeverityList() {
        return Arrays.stream(SeverityLevel.values())
                .map(SeverityLevel::toString).collect(Collectors.toList());
    }
}