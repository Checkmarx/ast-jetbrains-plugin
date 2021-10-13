package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Marks actions as Cx actions, allowing them to retrieve the Cx tool window in order to trigger effects on the UI
 */
public interface CxToolWindowAction extends DumbAware {

    /**
     * Get the Cx tool window for a given event.
     *
     * @param e event triggering the action
     * @return the cx tool window containing the action
     */
    @Nullable
    default CxToolWindowPanel getCxToolWindowPanel(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return null;
        }
        return getCxToolWindowPanel(e.getProject());
    }

    /**
     * Get the Cx tool window for a given project
     *
     * @param project project in which the action was triggered
     * @return the cx tool window containing the action
     */
    @Nullable
    default CxToolWindowPanel getCxToolWindowPanel(@NotNull Project project) {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
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

    /**
     * Refresh the panel to reflect changes.
     *
     * @param project Project to get the panel from
     */
    default void refreshPanel(@NotNull Project project) {
        Optional.ofNullable(getCxToolWindowPanel(project)).ifPresent(CxToolWindowPanel::refreshPanel);
    }
}
