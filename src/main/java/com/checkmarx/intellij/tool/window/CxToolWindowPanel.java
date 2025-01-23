package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.*;
import com.checkmarx.intellij.commands.TenantSetting;
import com.checkmarx.intellij.commands.results.obj.ResultGetState;
import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.components.TreeUtils;
import com.checkmarx.intellij.project.ProjectResultsService;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.tool.window.actions.StartScanAction;
import com.checkmarx.intellij.tool.window.actions.filter.FilterBaseAction;
import com.checkmarx.intellij.tool.window.actions.selection.ResetSelectionAction;
import com.checkmarx.intellij.tool.window.actions.selection.RootGroup;
import com.checkmarx.intellij.tool.window.results.tree.ResultsTreeFactory;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.intellij.util.ui.JBUI.Panels.simplePanel;

/**
 * Handles drawing the checkmarx tool window.
 * UI and internal state should only be manipulated in the single threaded context of the swing EDT, through
 * {@link ApplicationManager#getApplication()} and
 * {@link com.intellij.openapi.application.Application#invokeLater(Runnable)}.
 * Any async calls on other threads should be effectively static or this class will need synchronization!
 */
public class CxToolWindowPanel extends SimpleToolWindowPanel implements Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxToolWindowPanel.class);

    // pattern for validating UUIDs provided in the scan id field
    private static final Pattern uuidPattern = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // UI state
    // divides the main panel in tree|details sections
    private final OnePixelSplitter treeDetailsSplitter = new OnePixelSplitter(false, 0.3f);
    // divides the tree section in scanIdField|resultsTree sections
    private final OnePixelSplitter scanTreeSplitter = new OnePixelSplitter(true, 0.1f);
    // field to input a scan id
    private SearchTextField scanIdField = new SearchTextField();

    // Internal state
    private final List<GroupBy> groupByList = new ArrayList<>(GroupBy.DEFAULT_GROUP_BY);
    @Getter
    private ResultGetState currentState = new ResultGetState();
    private Tree currentTree = null;
    private boolean getResultsInProgress = false;

    @Getter
    private RootGroup rootGroup;

    private final Project project;
    // service for indexing current results
    private final ProjectResultsService projectResultsService;

    public CxToolWindowPanel(@NotNull Project project) {
        super(false, true);

        this.project = project;
        this.projectResultsService = project.getService(ProjectResultsService.class);

        Runnable r = () -> {
            if (new GlobalSettingsComponent().isValid()) {
                drawMainPanel();
            } else {
                drawAuthPanel();
                projectResultsService.indexResults(project, Results.emptyResults);
            }
        };

        // Establish message bus connection before subscribing
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)
                .subscribe(SettingsListener.SETTINGS_APPLIED, r::run);
        ApplicationManager.getApplication().getMessageBus().connect(this)
                .subscribe(FilterBaseAction.FILTER_CHANGED, this::changeFilter);

        r.run();
    }


    /**
     * Creates the main panel UI for results.
     */
    private void drawMainPanel() {
        removeAll();
        currentState.setScanIdFieldValue(null);

        ActionToolbar mainToolbar = getActionToolbar();
        ResetSelectionAction resetSelectionAction = (ResetSelectionAction) mainToolbar.getActions()
                .stream()
                .filter(a -> a instanceof ResetSelectionAction)
                                                                                      .findFirst()
                                                                                      .orElse(null);

        // update scan buttons visibility based on tenant settings
        CompletableFuture.supplyAsync(() -> {
            try {
                return TenantSetting.isScanAllowed();
            } catch (Exception e) {
                LOGGER.error(e);
                return false;
            }
        }).thenAccept(ideScansAllowed -> ApplicationManager.getApplication().invokeLater(() -> StartScanAction.setUserHasPermissionsToScan(ideScansAllowed)));

        // root group for project - branch - scan selection
        rootGroup = new RootGroup(project, resetSelectionAction);

        // listener to get results when enter is pressed on scan id field
        scanIdField = new SearchTextField();
        scanIdField.addKeyboardListener(new OnEnterGetResults());

        // split vertical the scan id field and the panel for results tree
        scanTreeSplitter.setResizeEnabled(false);
        scanTreeSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        scanTreeSplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE);
        scanTreeSplitter.setFirstComponent(simplePanel(scanIdField));
        scanTreeSplitter.setSecondComponent(simplePanel());

        // split horizontal the scan id field/results tree and the result details panel
        treeDetailsSplitter.setFirstComponent(scanTreeSplitter);
        treeDetailsSplitter.setSecondComponent(simplePanel());

        // set content and main toolbar
        SimpleToolWindowPanel treePanel = new SimpleToolWindowPanel(true, true);
        treePanel.setToolbar(getActionToolbar(rootGroup, true).getComponent());
        treePanel.setContent(treeDetailsSplitter);
        setContent(treePanel);
        setToolbar(mainToolbar.getComponent());
    }

    /**
     * Draw a panel with logo and a button to settings, when settings are invalid
     */
    private void drawAuthPanel() {
        removeAll();
        JPanel wrapper = new JPanel(new GridBagLayout());

        JPanel panel = new JPanel(new GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1));

        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        panel.add(new JBLabel(CxIcons.CHECKMARX_80), constraints);

        JButton comp = new JButton(Bundle.message(Resource.OPEN_SETTINGS_BUTTON));
        comp.addActionListener(e -> ShowSettingsUtil.getInstance()
                                                    .showSettingsDialog(project, GlobalSettingsConfigurable.class));

        constraints = new GridConstraints();
        constraints.setRow(1);
        panel.add(comp, constraints);

        wrapper.add(panel);

        setContent(wrapper);
    }

    @Override
    public void dispose() {

    }

    /**
     * Trigger drawing the results by a selection
     *
     * @param scanId selected scan id
     */
    public void selectScan(String scanId) {
        if (Utils.validThread()) {
            triggerDrawResultsTree(scanId, false);
        }
    }

    /**
     * Expand all rows in the results tree.
     * Can only be done in the context of the Swing EDT and when a get for results is not in progress.
     */
    public void expandAll() {
        if (Utils.validThread() && !getResultsInProgress && currentTree != null) {
            for (int i = 0; i < currentTree.getRowCount(); i++) {
                currentTree.expandRow(i);
            }
        }
    }

    /**
     * Collapse all rows in the results tree.
     * Can only be done in the context of the Swing EDT and when a get for results is not in progress.
     */
    public void collapseAll() {
        if (Utils.validThread() && !getResultsInProgress && currentTree != null) {
            for (int i = 0; i < currentTree.getRowCount(); i++) {
                currentTree.collapseRow(i);
            }
            treeDetailsSplitter.setSecondComponent(simplePanel());
        }
    }

    /**
     * Add or remove a groupBy to the list for applying
     *
     * @param groupBy  groupBy
     * @param selected whether it is selected (add) or deselected (remove)
     */
    public void changeGroupBy(GroupBy groupBy, boolean selected) {
        if (!Utils.validThread()) {
            return;
        }

        if (selected) {
            groupByList.add(groupBy);
            groupByList.sort(Enum::compareTo);
        } else {
            groupByList.remove(groupBy);
        }
        drawTree();
    }


    public void changeFilter() {
        if (!Utils.validThread()) {
            return;
        }

        drawTree();
    }

    /**
     * Refresh and redraw the panel.
     * Getting and setting the same content forces swing to redraw without rebuilding all the objects.
     */
    public void refreshPanel() {
        if (!Utils.validThread()) {
            return;
        }
        Optional.ofNullable(getContent()).ifPresent(this::setContent);
    }

    /**
     * Completely reset the main panel state
     */
    public void resetPanel() {
        if (!Utils.validThread()) {
            return;
        }
        rootGroup.setEnabled(false);
        currentState = new ResultGetState();
        projectResultsService.indexResults(project, Results.emptyResults);
        scanIdField.setText("");
        scanTreeSplitter.setSecondComponent(simplePanel());
        treeDetailsSplitter.setSecondComponent(simplePanel());
        rootGroup.reset();
    }

    /**
     * Trigger drawing results for a given scan id.
     * Validates the following before triggering:
     * - The thread is the Swing EDT thread;
     * - An async call to get results is not in progress;
     * - the scan id has changed
     * - the scan id is a valid UUID or blank (latest)
     *
     * @param scanIdValue scan id to get results
     */
    private void triggerDrawResultsTree(String scanIdValue, boolean overrideSelections) {

        if (!Utils.validThread() || getResultsInProgress || Objects.equals(scanIdValue,
                                                                           currentState.getScanIdFieldValue())) {
            return;
        }

        currentState = new ResultGetState();

        if (!StringUtils.isBlank(scanIdValue) && !uuidPattern.matcher(scanIdValue).matches()) {
            currentState.setMessage(Bundle.message(Resource.INVALID_SCAN_ID));
            updateDisplay();
            return;
        }

        // disable selections while in progress
        rootGroup.setEnabled(false);

        LOGGER.info("Getting results for scan " + scanIdValue);

        getResultsInProgress = true;

        currentState.setMessage(Bundle.message(Resource.GETTING_RESULTS));
        updateDisplay();

        // updates to variables wrapped in an invokeLater call so the Swing EDT performs the update
        // in a single threaded manner
        Results.getResults(scanIdValue)
               .thenAcceptAsync((newState) -> ApplicationManager.getApplication().invokeLater(() -> {
                   currentState = newState;
                   if (overrideSelections) {
                       // don't enable rootGroup immediately, override is async and will enable when done
                       rootGroup.override(currentState.getScanId());
                   } else {
                       // re-enable selections
                       rootGroup.setEnabled(true);
                   }
                   getResultsInProgress = false;
                   updateDisplay();
               }));
    }

    /**
     * Update the display with the current state
     */
    private void updateDisplay() {
        if (!Utils.validThread()) {
            return;
        }

        projectResultsService.indexResults(project, currentState.getResultOutput());
        if (currentState.getMessage() != null) {
            LOGGER.info(String.format("Cannot show results: %s", currentState.getMessage()));
            scanTreeSplitter.setSecondComponent(TreeUtils.labelTreePanel(currentState.getMessage()));
        } else {
            LOGGER.info("Updating state for scan " + currentState.getScanId());
            drawTree();
        }
    }

    /**
     * Draw the results tree
     */
    private void drawTree() {
        if (!Utils.validThread() || currentState.getScanId() == null) {
            return;
        }

        currentTree = ResultsTreeFactory.buildResultsTree(currentState.getScanId(),
                                                          currentState.getResultOutput(),
                                                          project,
                                                          groupByList,
                                                          GlobalSettingsState.getInstance().getFilters(),
                                                          currentState.isLatest());
        currentTree.addTreeSelectionListener(new OnSelectShowDetail());
        scanTreeSplitter.setSecondComponent(TreeUtils.treePanel(currentTree));
    }

    /**
     * {@link TreeSelectionListener} to show details of a selected result on the right component of the tool window.
     */
    private class OnSelectShowDetail implements TreeSelectionListener {

        @SneakyThrows
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                Object selected = path.getLastPathComponent();
                Tree tree = (Tree) e.getSource();
                if (tree.getModel().isLeaf(selected) && selected instanceof ResultNode) {
                    ResultNode resultNode = (ResultNode) selected;
                    treeDetailsSplitter.setSecondComponent(resultNode.buildResultPanel(() -> {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            drawTree();
                            DefaultMutableTreeNode root = (DefaultMutableTreeNode) currentTree.getModel().getRoot();
                            Enumeration<TreeNode> enumeration = root.depthFirstEnumeration();
                            while (enumeration.hasMoreElements()) {
                                TreeNode treeNode = enumeration.nextElement();
                                if (tree.getModel().isLeaf(treeNode) && treeNode instanceof ResultNode) {
                                    ResultNode node = (ResultNode) treeNode;
                                    if (node.getResult() == resultNode.getResult()) {
                                        currentTree.expandPath(new TreePath(node.getPath()).getParentPath());
                                        break;
                                    }
                                }
                            }
                            valueChanged(new TreeSelectionEvent(currentTree, path, true, e.getOldLeadSelectionPath(), e.getNewLeadSelectionPath()));
                        });
                    }, () -> refreshPanel()));
                }
            }
        }
    }

    @NotNull
    private static ActionToolbar getActionToolbar() {
        ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction(Constants.ACTION_GROUP_ID);
        return getActionToolbar(group, false);
    }

    @NotNull
    private static ActionToolbar getActionToolbar(ActionGroup group, boolean horizontal) {
        ActionToolbar toolbar = ActionManager.getInstance()
                                             .createActionToolbar(Constants.TOOL_WINDOW_ID, group, horizontal);
        toolbar.setTargetComponent(toolbar.getComponent());
        return toolbar;
    }

    /**
     * {@link KeyListener} to get results for the scan id in scanIdField on {@link KeyEvent#VK_ENTER}.
     */
    private class OnEnterGetResults implements KeyListener {

        @Override
        public void keyTyped(KeyEvent e) {
            // do nothing
        }

        @Override
        public void keyPressed(KeyEvent e) {
            // do nothing
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getExtendedKeyCode() == KeyEvent.VK_ENTER) {
                triggerDrawResultsTree(scanIdField.getText().trim(), true);
            }
        }
    }

    public interface CxRefreshHandler {
        void refresh();
    }
}
