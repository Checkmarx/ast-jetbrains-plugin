package com.checkmarx.intellij.tool.window.results.tree.nodes;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;

public class NonLeafNode extends DefaultMutableTreeNode {

    private int children = 0;

    public NonLeafNode(String userObject) {
        super(userObject);
    }

    @Override
    public void add(MutableTreeNode newChild) {
        super.add(newChild);
        incrementChildren();
    }

    public void incrementChildren() {
        children++;
    }

    @Override
    public String toString() {
        return super.toString() + " (" + children + ")";
    }
}
