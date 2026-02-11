package com.checkmarx.intellij.common.window.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;

import javax.swing.*;
import java.awt.*;

/**
 * Utility class for expand and collapse actions.
 */
public class ExpandCollapseCommon {

    /**
     * Dynamically gets the target tree from the event's ToolWindow context.
     */
    protected JTree getTargetTree(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return null;

        // Get the ToolWindow directly from the event's DataContext
        ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
        if (toolWindow == null) return null;

        Content content = toolWindow.getContentManager().getSelectedContent();
        if (content == null) return null;

        return findTreeInComponent(content.getComponent());
    }

    /**
     * Find a tree in a component.
     *
     * @param compObj {@link Component}
     * @return {@link JTree}
     */
    protected JTree findTreeInComponent(Component compObj) {
        if (compObj == null) return null;

        if (compObj instanceof JTree) return (JTree) compObj;

        if (!(compObj instanceof Container)) return null;

        for (Component child : ((Container) compObj).getComponents()) {
            JTree tree = findTreeInComponent(child);
            if (tree != null) return tree;
        }
        return null;
    }
}
