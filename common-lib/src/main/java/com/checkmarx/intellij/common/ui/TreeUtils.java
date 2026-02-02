package com.checkmarx.intellij.common.ui;

import com.intellij.ui.treeStructure.Tree;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Utils for drawing tree panels
 */
public final class TreeUtils {
    private TreeUtils() {
    }

    /**
     * Simple tree panel with a single label. Useful for showing messages when the tree has no contents.
     *
     * @param text label
     * @return tree panel
     */
    public static JComponent labelTreePanel(String text) {
        return treePanel(new Tree(new DefaultMutableTreeNode(text)));
    }

    /**
     * Scrollable panel with a tree.
     *
     * @param tree tree to display
     * @return panel with tree
     */
    public static JPanel treePanel(Tree tree) {
        JPanel panel = new JPanel();
        panel.setLayout(new CardLayout());
        panel.add(PaneUtils.inScrollPane(tree), "tree");
        return panel;
    }
}
