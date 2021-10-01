package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class BranchSelectionGroup extends BaseSelectionGroup {

    public BranchSelectionGroup(@NotNull Project project) {
        super(project);
    }

    @Override
    protected String defaultValue() {
        return "main";
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_BRANCH_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.BRANCH_SELECT_PREFIX;
    }
}
