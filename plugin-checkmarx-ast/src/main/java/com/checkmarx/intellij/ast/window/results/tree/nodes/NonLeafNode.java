package com.checkmarx.intellij.ast.window.results.tree.nodes;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Non-leaf node (not a result) for engines and group by
 */
public class NonLeafNode extends DefaultMutableTreeNode {

    private int subTreeSize = 0;

    /**
     * Index of non-leaf children by their label, so looking up an existing group node
     * is O(1) instead of a linear scan over all children.
     */
    private final Map<String, NonLeafNode> nonLeafChildrenByKey = new HashMap<>();

    public NonLeafNode(String userObject) {
        super(userObject);
    }

    /**
     * Look up an already created non-leaf child by its label.
     *
     * @param key child label
     * @return the child, or null if this node has no such child
     */
    public NonLeafNode getNonLeafChild(String key) {
        return nonLeafChildrenByKey.get(key);
    }

    /**
     * Increment the size of the node's sub-tree.
     */
    public void incrementSubTreeSize() {
        subTreeSize++;
    }

    /**
     * {@inheritDoc}
     * Inserts the child at its sorted position according to the comparator.
     * <p>
     * The position is found with a binary search over the already sorted children, so
     * building a node with n children costs O(n log n) comparisons. Previously the whole
     * children vector was re-sorted on every insert, which was O(n^2 log n) and froze the
     * EDT on scans with many results.
     *
     * @param comparator comparator defining the order of the children, or null to append
     */
    public void add(MutableTreeNode newChild, Comparator<String> comparator) {
        if (comparator == null) {
            super.add(newChild);
        } else {
            String key = getTreeNodeUserObject(newChild);
            int low = 0;
            int high = getChildCount();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (comparator.compare(getTreeNodeUserObject(getChildAt(mid)), key) <= 0) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            super.insert(newChild, low);
        }
        if (newChild instanceof NonLeafNode) {
            nonLeafChildrenByKey.put(getTreeNodeUserObject(newChild), (NonLeafNode) newChild);
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
