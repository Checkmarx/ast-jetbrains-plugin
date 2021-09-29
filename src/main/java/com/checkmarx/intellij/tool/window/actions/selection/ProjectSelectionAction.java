package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectSelectionAction extends AnAction implements CxToolWindowAction {

    public ProjectSelectionAction(String name) {
        super(name);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = getEventProject(e);
        if (project != null) {
            PropertiesComponent.getInstance(project)
                               .setValue("Checkmarx.SelectedProject", e.getPresentation().getText());
        }
    }
}
