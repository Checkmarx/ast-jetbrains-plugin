package com.checkmarx.intellij.ast.window.results.tree.nodes;

import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Non-leaf node (engine or group-by); non-leaf siblings must have unique labels. */
public class NonLeafNode extends DefaultMutableTreeNode {

    private int subTreeSize = 0;

    // label -> non-leaf child index; kept in sync by insert(), remove(int), setUserObject() and clone()
    private Map<String, NonLeafNode> nonLeafChildrenByKey = new HashMap<>();

    public NonLeafNode(String userObject) {
        super(userObject);
    }

    /** Returns the attached non-leaf child with this label, or null if there is none. */
    @Nullable
    public NonLeafNode getNonLeafChild(String key) {
        return nonLeafChildrenByKey.get(key);
    }

    /** Increments the size of this node's sub-tree. */
    public void incrementSubTreeSize() {
        subTreeSize++;
    }

    /** Inserts the child at its sorted position via binary search, or appends when comparator is null. */
    public void add(MutableTreeNode newChild, Comparator<String> comparator) {
        if (comparator == null) {
            add(newChild);
            return;
        }
        if (newChild != null && newChild.getParent() == this) {
            remove(newChild); // detach first so the binary search sees the correct sibling list
        }
        String key = labelOf(newChild);
        int low = 0;
        int high = getChildCount();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (comparator.compare(labelOf(getChildAt(mid)), key) <= 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        insert(newChild, low); // virtual call so the override keeps the index in sync
    }

    /** All DefaultMutableTreeNode attach paths end here, so this is where the index is populated. */
    @Override
    public void insert(MutableTreeNode newChild, int childIndex) {
        super.insert(newChild, childIndex);
        if (newChild instanceof NonLeafNode) {
            nonLeafChildrenByKey.put(labelOf(newChild), (NonLeafNode) newChild);
        }
    }

    /** All DefaultMutableTreeNode detach paths end here, so this is where the index is evicted. */
    @Override
    public void remove(int childIndex) {
        TreeNode child = getChildAt(childIndex);
        super.remove(childIndex);
        if (child instanceof NonLeafNode) {
            nonLeafChildrenByKey.remove(labelOf(child), child); // only evict if the entry is this exact node
        }
    }

    /** Re-keys this node in its parent's index when the label changes. */
    @Override
    public void setUserObject(Object userObject) {
        String oldKey = labelOf(this);
        super.setUserObject(userObject);
        String newKey = labelOf(this);
        TreeNode parent = getParent();
        if (parent instanceof NonLeafNode && !oldKey.equals(newKey)) {
            Map<String, NonLeafNode> index = ((NonLeafNode) parent).nonLeafChildrenByKey;
            index.remove(oldKey, this);
            index.put(newKey, this);
        }
    }

    /** The childless copy gets an empty index and a zero sub-tree size. */
    @Override
    public Object clone() {
        NonLeafNode copy = (NonLeafNode) super.clone();
        copy.nonLeafChildrenByKey = new HashMap<>();
        copy.subTreeSize = 0;
        return copy;
    }

    /** Appends the sub-tree size after the node label. */
    @Override
    public String toString() {
        return super.toString() + " (" + subTreeSize + ")";
    }

    // single non-throwing label derivation shared by the sorted insert and the index
    private static String labelOf(TreeNode node) {
        return String.valueOf(((DefaultMutableTreeNode) node).getUserObject());
    }
}
