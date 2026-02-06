package com.checkmarx.intellij.common.window.actions;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Action to expand the scan results tree.
 */
public class ExpandAllAction extends AnAction {

    private final ExpandCollapseCommon expandCollapseCommon = new ExpandCollapseCommon();

    public ExpandAllAction() {
        super(Bundle.messagePointer(Resource.EXPAND_ALL_ACTION));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JTree tree = expandCollapseCommon.getTargetTree(e);
        if (tree != null) {
            expandAll(tree);
        }
    }

    /**
     * Expand all rows in the tree.
     *
     * @param tree tree to expand
     */
    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}

