package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RootGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    private final ActionGroup projectActionGroup;
    private final ActionGroup branchActionGroup;
    private final ActionGroup scanActionGroup;

    public RootGroup(Project project) {
        super();
        scanActionGroup = new ScanSelectionGroup(project);
        branchActionGroup = new BranchSelectionGroup(project, scanActionGroup);
        projectActionGroup = new ProjectSelectionGroup(project, branchActionGroup, scanActionGroup);
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{
                projectActionGroup, branchActionGroup, scanActionGroup
        };
    }
}
