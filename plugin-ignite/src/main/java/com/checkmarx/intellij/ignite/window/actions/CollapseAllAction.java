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
 * Action to collapse the results tree.
 */
@SuppressWarnings("ComponentNotRegistered")
public class CollapseAllAction extends AnAction {

    public CollapseAllAction() {
        super(Bundle.messagePointer(Resource.COLLAPSE_ALL_ACTION));
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        JTree tree = getTargetTree(e);
        if (tree != null) {
            collapseAll(tree);
        }
    }

    private JTree getTargetTree(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return null;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Checkmarx Developer Assist");
        if (toolWindow == null) return null;

        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content == null) return null;

        JComponent component = content.getComponent();

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

    private void collapseAll(JTree tree) {
        // Collapse rows from bottom to top to avoid issues with row count changing during collapsing
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            tree.collapseRow(i);
        }
    }
}

