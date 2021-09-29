package com.checkmarx.intellij.tool.window.actions.selection;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;

public class BranchSelectionGroup extends BaseSelectionGroup {

    public BranchSelectionGroup(Project project, ActionGroup scanActionGroup) {
        super();
    }

    @Override
    protected String getPrefix() {
        return "Branch: ";
    }
}
