package com.checkmarx.intellij.tool.window.results.tree;

import com.checkmarx.ast.results.Results;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.CustomResultState;
import com.checkmarx.intellij.tool.window.GroupBy;
import com.checkmarx.intellij.tool.window.Severity;
import com.checkmarx.intellij.tool.window.actions.filter.Filterable;
import com.checkmarx.intellij.tool.window.results.tree.nodes.NonLeafNode;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.hover.TreeHoverListener;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.*;

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
        for (Result result : results.getResults()) {

            if (enabledFilters.contains(Severity.valueOf(result.getSeverity())) && enabledFilters.stream().anyMatch(f->{
                if (f instanceof CustomResultState){
                   return f.tooltipSupplier().get().equals(result.getState());
                } else {
                    return false;
                }
            })) {
                addResultToEngine(project,
                        groupByList,
                        engineNodes.computeIfAbsent(result.getType(), NonLeafNode::new),
                        result, scanId);
            }
        }

        for (DefaultMutableTreeNode node : engineNodes.values()) {
            ((DefaultMutableTreeNode) tree.getModel().getRoot()).add(node);
        }

        return tree;
    }

    private static void addResultToEngine(Project project,
                                          List<GroupBy> groupByList,
                                          NonLeafNode parent,
                                          Result result,
                                          String scanId) {
        for (GroupBy groupBy : groupByList) {
            NonLeafNode child = null;
            String childKey = groupBy.getFunction().apply(result);
            if (StringUtils.isBlank(childKey)) {
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
