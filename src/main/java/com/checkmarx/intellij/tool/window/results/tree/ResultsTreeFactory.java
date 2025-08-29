package com.checkmarx.intellij.tool.window.results.tree;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.ast.results.result.ScaPackageData;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.checkmarx.intellij.tool.window.results.tree.nodes.NonLeafNode;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.hover.TreeHoverListener;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.tool.window.GroupBy.SCA_TYPE;

/**
 * Factory for result trees
 */
public class ResultsTreeFactory {

    /**
     * Get results from the CLI in a tree format.
     *
     * @param scanId         scan id
     * @param results        list of results
     * @param project        context project
     * @param groupByList    list of {@link GroupBy}
     * @param enabledFilters set of enabled {@link Filterable}
     * @param latest         whether the scan id is the latest
     * @return tree with results
     */
    @NotNull
    public static Tree buildResultsTree(String scanId,
                                        Results results,
                                        Project project,
                                        List<GroupBy> groupByList,
                                        Set<Filterable> enabledFilters,
                                        boolean latest) {

        Tree tree = createTree(scanId, latest);

        Map<String, NonLeafNode> engineNodes = new HashMap<>();
        // Make sure sca type groupBy is always applied first
        groupByList.remove(SCA_TYPE);
        groupByList.add(0, SCA_TYPE);

        // Collect all enabled filter values into a single set
        Set<String> enabledFilterValues = enabledFilters.stream()
                .map(Filterable::getFilterValue)
                .collect(Collectors.toSet());

        boolean isSCAHideDevTestDependencyEnabled = Utils.isFilterEnabled(enabledFilterValues, Constants.SCA_HIDE_DEV_TEST_DEPENDENCIES);

        // Stream over results and filter
        results.getResults().stream()
                .filter(result -> enabledFilterValues.contains(result.getSeverity())
                        && enabledFilterValues.contains(result.getState()))
                .forEach(result -> {
                    /*
                     * If a result is for SCA - dev or test dependency, and SCA Hide Dev & Test Dependency filter is enabled,
                     * then ignore a result to add in the engine
                     */
                    if (!isDevTestDependency(result, isSCAHideDevTestDependencyEnabled)) {
                                addResultToEngine(project, groupByList,
                                        engineNodes.computeIfAbsent(result.getType(), NonLeafNode::new),
                                        result, scanId);
                            }
                        }
                );
        for (DefaultMutableTreeNode node : engineNodes.values()) {
            ((DefaultMutableTreeNode) tree.getModel().getRoot()).add(node);
        }
        return tree;
    }

    /**
     * This method is used to check if SCA Hide Dev & Test Dependency filter is enabled.
     * If filter is enabled and a result type is SCA, then extract a sca package details from the result
     * and check if vulnerability for dev or test dependency.
     *
     * @param result                            {@link Result} contains a scan result
     * @param isSCAHideDevTestDependencyEnabled boolean value which tells SCA Hide Dev & Test Dependency filter enabled or not
     * @return true if SCA Hide Dev & Test Dependency filter is enabled and a result is for SCA and belongs to dev or test dependency, otherwise false
     */
    private static boolean isDevTestDependency(Result result, boolean isSCAHideDevTestDependencyEnabled) {
        if (isSCAHideDevTestDependencyEnabled && result != null && result.getType().equalsIgnoreCase(Constants.SCAN_TYPE_SCA)) {
            ScaPackageData scaPackageData = result.getData() != null ? result.getData().getScaPackageData() : null;
            return (scaPackageData != null && (scaPackageData.isDevelopmentDependency() || scaPackageData.isTestDependency()));
        }
        return false;
    }

    private static void addResultToEngine(Project project,
                                          List<GroupBy> groupByList,
                                          NonLeafNode parent,
                                          Result result,
                                          String scanId) {
        for (GroupBy groupBy : groupByList) {
            NonLeafNode child = null;
            String childKey = groupBy.getFunction().apply(result);
            if (Utils.isBlank(childKey)) {
                continue;
            }
            // search for the child node
            Iterator<TreeNode> it = parent.children().asIterator();
            while (it.hasNext()) {
                TreeNode node = it.next();
                if (!(node instanceof NonLeafNode)) continue;
                NonLeafNode newChild = (NonLeafNode) node;
                if (childKey.equals(newChild.getUserObject())) {
                    child = newChild;
                    break;
                }
            }
            if (child == null) {
                // if the parent was not found, create a new one
                child = new NonLeafNode(childKey);
                parent.add(child, groupBy.getComparator());
            }
            parent.incrementSubTreeSize();
            parent = child;
        }
        parent.add(new ResultNode(result, project, scanId), String::compareTo);
        parent.incrementSubTreeSize();
    }

    @NotNull
    private static Tree createTree(String scanId, boolean latest) {
        Object rootNodeLabel = Bundle.message(Resource.RESULTS_TREE_HEADER, scanId);
        rootNodeLabel += Utils.formatLatest(latest);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootNodeLabel);

        Tree tree = new Tree(root);

        tree.setCellRenderer(new ResultsTreeCellRenderer());

        tree.setUI(new DefaultTreeUI());

        //noinspection UnstableApiUsage
        TreeHoverListener.DEFAULT.addTo(tree);

        return tree;
    }
}
