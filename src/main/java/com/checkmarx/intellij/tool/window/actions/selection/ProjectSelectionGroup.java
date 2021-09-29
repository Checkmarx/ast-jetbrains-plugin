package com.checkmarx.intellij.tool.window.actions.selection;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectSelectionGroup extends BaseSelectionGroup {

    public ProjectSelectionGroup(@NotNull Project project,
                                 ActionGroup branchActionGroup,
                                 ActionGroup scanActionGroup) {
        super();
    }

    @Override
    protected String getPrefix() {
        return "Project: ";
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{
                new ProjectSelectionAction("Project1", select),
                new ProjectSelectionAction("Project2", select)
        };
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return false;
    }
}
