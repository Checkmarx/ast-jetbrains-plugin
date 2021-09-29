package com.checkmarx.intellij.tool.window.actions.selection;

import com.intellij.openapi.project.Project;

public class ScanSelectionGroup extends BaseSelectionGroup {

    public ScanSelectionGroup(Project project) {
        super();
    }

    @Override
    protected String getPrefix() {
        return "Scan: ";
    }
}
