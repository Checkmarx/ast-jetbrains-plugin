package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class BranchSelectionGroup extends BaseSelectionGroup {

    public BranchSelectionGroup(@NotNull Project project) {
        super(project);
    }

    @Override
    protected @NotNull String getTitle() {
        return Bundle.message(Resource.BRANCH_SELECT_PREFIX)
               + ": "
               + Bundle.message(Resource.NONE_SELECTED);
    }
}
