package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ScanSelectionGroup extends BaseSelectionGroup {

    public ScanSelectionGroup(@NotNull Project project) {
        super(project);
        addChild("none");
        addChild("xs");
        addChild("BIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIG");
        addChild("NormalLength");
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_SCAN_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.SCAN_SELECT_PREFIX;
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
