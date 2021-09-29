package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.results.ResultGetState;
import com.checkmarx.intellij.commands.results.Results;
import com.checkmarx.intellij.components.TreeUtils;
import com.checkmarx.intellij.project.ProjectResultsService;
import com.checkmarx.intellij.tool.window.actions.selection.RootGroup;
import com.checkmarx.intellij.tool.window.results.tree.GroupBy;
import com.checkmarx.intellij.tool.window.results.tree.ResultsTreeFactory;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private final SearchTextField scanIdField = new SearchTextField();

    // Internal state
    private final List<GroupBy> groupByList = new ArrayList<>(Collections.singletonList(GroupBy.DEFAULT_GROUP_BY));
    private ResultGetState currentState = new ResultGetState();
    private Tree currentTree = null;
    private boolean getResultsInProgress = false;

    private final Project project;
    private final ProjectResultsService projectResultsService;

    /**
     * Creates the basic panel UI and triggers an async call to get the latest results and draw them.
     *
     * @param project current project
     */
    public CxToolWindowPanel(@NotNull Project project) {
        super(false, true);

        this.project = project;
        this.projectResultsService = project.getService(ProjectResultsService.class);

        scanIdField.addKeyboardListener(new OnEnterGetResults());

        scanTreeSplitter.setResizeEnabled(false);
        scanTreeSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        scanTreeSplitter.setLackOfSpaceStrategy(Splitter.LackOfSpaceStrategy.HONOR_THE_FIRST_MIN_SIZE);
        scanTreeSplitter.setFirstComponent(simplePanel(scanIdField));
        scanTreeSplitter.setSecondComponent(simplePanel());

        treeDetailsSplitter.setFirstComponent(scanTreeSplitter);
        BorderLayoutPanel component = simplePanel();
        treeDetailsSplitter.setSecondComponent(component);

        SimpleToolWindowPanel treePanel = new SimpleToolWindowPanel(true, true);
        treePanel.setToolbar(getActionToolbar(new RootGroup(project), true).getComponent());
        treePanel.setContent(treeDetailsSplitter);

        setToolbar(getActionToolbar(project, Constants.ACTION_GROUP_ID, false).getComponent());
        setContent(treePanel);

        // when starting get the latest scan id
        triggerDrawResultsTree("");
    }

    @Override
    public void dispose() {

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
    private void triggerDrawResultsTree(String scanIdValue) {

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

        LOGGER.info("Getting results for scan " + scanIdValue);

        getResultsInProgress = true;

        currentState.setMessage(Bundle.message(Resource.GETTING_RESULTS));
        updateDisplay();

        // updates to variables wrapped in an invokeLater call so the Swing EDT performs the update
        // in a single threaded manner
        Results.getResults(scanIdValue)
               .thenAcceptAsync((newState) -> ApplicationManager.getApplication().invokeLater(() -> {
                   currentState = newState;
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
        if (!Utils.validThread()) {
            return;
        }

        currentTree = ResultsTreeFactory.buildResultsTree(currentState.getScanId(),
                                                          currentState.getResultOutput(),
                                                          project,
                                                          groupByList,
                                                          currentState.isLatest());
        currentTree.addTreeSelectionListener(new OnSelectShowDetail());
        scanTreeSplitter.setSecondComponent(TreeUtils.treePanel(currentTree));
    }

    /**
     * {@link TreeSelectionListener} to show details of a selected result on the right component of the tool window.
     */
    private class OnSelectShowDetail implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            TreePath path = e.getNewLeadSelectionPath();
            if (path != null) {
                Object selected = path.getLastPathComponent();
                Tree tree = (Tree) e.getSource();
                if (tree.getModel().isLeaf(selected) && selected instanceof ResultNode) {
                    ResultNode resultNode = (ResultNode) selected;
                    treeDetailsSplitter.setSecondComponent(resultNode.buildResultPanel());
                }
            }
        }
    }

    @NotNull
    private static ActionToolbar getActionToolbar(Project project, String actionGroupId, boolean horizontal) {
        ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction(actionGroupId);
        return getActionToolbar(group, horizontal);
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
                triggerDrawResultsTree(scanIdField.getText().trim());
            }
        }
    }
}
