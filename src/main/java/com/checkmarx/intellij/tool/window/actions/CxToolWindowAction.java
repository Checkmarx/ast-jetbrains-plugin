package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

public interface CxToolWindowAction extends DumbAware {

    default CxToolWindowPanel getCxToolWindowPanel(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return null;
        }
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(e.getProject());
        ToolWindow toolWindow = toolWindowManager.getToolWindow(Constants.TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return null;
        }
        ContentManager contentManager = toolWindow.getContentManager();
        Content content = contentManager.getContent(0);
        if (content == null) {
            return null;
        }
        return (CxToolWindowPanel) content.getComponent();
    }
}
