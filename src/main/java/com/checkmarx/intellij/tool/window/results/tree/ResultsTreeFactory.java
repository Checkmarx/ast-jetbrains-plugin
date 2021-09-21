package com.checkmarx.intellij.tool.window.results.tree;

import com.checkmarx.ast.results.structure.CxResult;
import com.checkmarx.ast.results.structure.CxResultOutput;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.tool.window.results.tree.nodes.NonLeafNode;
import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Factory for result trees
 */
public class ResultsTreeFactory {

    /**
     * Get results from the CLI in a tree format.
     *
     * @param scanId      scan id
     * @param results     list of results
     * @param project     context project
     * @param groupByList list of {@link GroupBy}
     * @param latest      whether the scan id is the latest
     * @return tree with results
     */
    @NotNull
    public static Tree buildResultsTree(String scanId,
                                        CxResultOutput results,
                                        Project project,
                                        List<GroupBy> groupByList,
                                        boolean latest) {

        Tree tree = createTree(scanId, latest);

        Map<String, NonLeafNode> engineNodes = new HashMap<>();
        for (CxResult result : results.getResults()) {
            addResultToEngine(project,
                              groupByList,
                              engineNodes.computeIfAbsent(result.getType(), NonLeafNode::new),
                              result);
        }

        for (DefaultMutableTreeNode node : engineNodes.values()) {
            ((DefaultMutableTreeNode) tree.getModel().getRoot()).add(node);
        }

        return tree;
    }

    private static void addResultToEngine(Project project,
                                          List<GroupBy> groupByList,
                                          NonLeafNode parent,
                                          CxResult result) {
        for (GroupBy groupBy : groupByList) {
            NonLeafNode child = null;
            String childKey = groupBy.getFunction().apply(result);
            // search for the child node
            Iterator<TreeNode> it = parent.children().asIterator();
            while (it.hasNext()) {
                NonLeafNode newChild = (NonLeafNode) it.next();
                if (childKey.equals(newChild.getUserObject())) {
                    child = newChild;
                    break;
                }
            }
            if (child == null) {
                // if the parent was not found, create a new one
                child = new NonLeafNode(childKey);
                parent.add(child);
            } else {
                // parent found, we are adding a new leaf to its subtree
                parent.incrementChildren();
            }
            parent = child;
        }
        parent.add(new ResultNode(result, project));
    }

    @NotNull
    private static Tree createTree(String scanId, boolean latest) {
        Object rootNodeLabel = Bundle.message(Resource.RESULTS_TREE_HEADER, scanId);
        rootNodeLabel += Utils.formatLatest(latest);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootNodeLabel);

        Tree tree = new Tree(root);

        tree.setCellRenderer(new ResultsTreeCellRenderer());

        return tree;
    }
}
