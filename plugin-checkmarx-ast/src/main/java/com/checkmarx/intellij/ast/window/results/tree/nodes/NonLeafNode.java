package com.checkmarx.intellij.ast.window.results.tree.nodes;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Comparator;

/**
 * Non-leaf node (not a result) for engines and group by
 */
public class NonLeafNode extends DefaultMutableTreeNode {

    private int subTreeSize = 0;

    public NonLeafNode(String userObject) {
        super(userObject);
    }

    /**
     * Increment the size of the node's sub-tree.
     */
    public void incrementSubTreeSize() {
        subTreeSize++;
    }

    /**
     * {@inheritDoc}
     * After adding child, sort the children vector according to the comparator
     *
     * @param comparator comparator to sort children
     */
    public void add(MutableTreeNode newChild, Comparator<String> comparator) {
        super.add(newChild);
        if (comparator != null) {
            super.children.sort((a, b) -> comparator.compare(getTreeNodeUserObject(a), getTreeNodeUserObject(b)));
        }
    }

    /**
     * {@inheritDoc}
     * Append the sub-tree size after the node label
     */
    @Override
    public String toString() {
        return super.toString() + " (" + subTreeSize + ")";
    }

    private static String getTreeNodeUserObject(TreeNode node) {
        return (String) ((DefaultMutableTreeNode) node).getUserObject();
    }
}
