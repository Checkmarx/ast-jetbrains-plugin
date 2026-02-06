package com.checkmarx.intellij.common.window.actions;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Action to collapse the scan results tree.
 * Dynamically identifies the ToolWindow from the event context.
 */
@SuppressWarnings("ComponentNotRegistered")
public class CollapseAllAction extends AnAction {

    private final ExpandCollapseCommon expandCollapseCommon = new ExpandCollapseCommon();

    public CollapseAllAction() {
        super(Bundle.messagePointer(Resource.COLLAPSE_ALL_ACTION));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JTree tree = expandCollapseCommon.getTargetTree(e);
        if (tree != null) {
            collapseAll(tree);
        }
    }

    /**
     * Collapse all rows in the tree.
     *
     * @param tree tree to collapse
     */
    private void collapseAll(JTree tree) {
        // Collapse rows from bottom to top to avoid issues with row count changing during collapsing
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            tree.collapseRow(i);
        }
    }
}

