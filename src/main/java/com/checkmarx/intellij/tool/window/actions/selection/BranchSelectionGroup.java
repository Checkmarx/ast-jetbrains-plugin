package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class BranchSelectionGroup extends BaseSelectionGroup {

    public BranchSelectionGroup(@NotNull Project project) {
        super(project);
        addChild("master");
        addChild("xs");
        addChild("BIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIG");
        addChild("NormalLength");
    }

    @Override
    protected String defaultValue() {
        return "master";
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_BRANCH_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.BRANCH_SELECT_PREFIX;
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
