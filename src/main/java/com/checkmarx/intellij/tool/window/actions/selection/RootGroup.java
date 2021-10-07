package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * Base group containing the project, branch and scan selection groups
 */
public class RootGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    public RootGroup(Project project) {
        super();
        ScanSelectionGroup scanSelectionGroup = new ScanSelectionGroup(project);
        addAll(new ProjectSelectionGroup(project, scanSelectionGroup), scanSelectionGroup);
    }
}
