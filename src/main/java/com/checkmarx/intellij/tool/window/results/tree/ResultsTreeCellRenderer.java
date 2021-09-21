package com.checkmarx.intellij.tool.window.results.tree;

import com.checkmarx.intellij.tool.window.results.tree.nodes.ResultNode;
import com.intellij.ide.util.treeView.NodeRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ResultsTreeCellRenderer extends NodeRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof ResultNode) {
            setIcon(((ResultNode) value).getIcon());
        }
    }
}
