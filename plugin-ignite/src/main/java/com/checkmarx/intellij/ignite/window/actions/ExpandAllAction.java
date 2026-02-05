package com.checkmarx.intellij.ignite.window.actions;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Action to expand the results tree.
 */
public class ExpandAllAction extends AnAction {

    public ExpandAllAction() {
        super(Bundle.messagePointer(Resource.EXPAND_ALL_ACTION));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JTree tree = getTargetTree(e);
        if (tree != null) {
            expandAll(tree);
        }
    }

    private JTree getTargetTree(AnActionEvent e) {
        // Attempt to get the current active tool window content component,
        // then find the tree inside it.

        Project project = e.getProject();
        if (project == null) return null;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Checkmarx Developer Assist");
        if (toolWindow == null) return null;

        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content == null) return null;

        JComponent component = content.getComponent();

        // Example: find the tree by name or traversing the component hierarchy
        return findTreeInComponent(component);
    }

    private JTree findTreeInComponent(Component comp) {
        if (comp instanceof JTree) return (JTree) comp;
        if (!(comp instanceof Container)) return null;
        for (Component child : ((Container) comp).getComponents()) {
            JTree tree = findTreeInComponent(child);
            if (tree != null) return tree;
        }
        return null;
    }

    private void expandAll(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }
}

