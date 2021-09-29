package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SelectionAction extends AnAction implements CxToolWindowAction {

    private final String property;

    public SelectionAction(String name, String property) {
        super(name);
        this.property = property;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = getEventProject(e);
        if (project != null) {
            PropertiesComponent.getInstance(project)
                               .setValue(property, e.getPresentation().getText());
            Optional.ofNullable(getCxToolWindowPanel(e)).ifPresent(CxToolWindowPanel::refreshToolbar);
        }
    }
}
