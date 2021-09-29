package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProjectSelectionGroup extends BaseSelectionGroup {

    public ProjectSelectionGroup(@NotNull Project project) {
        super(project);
        addChild(project.getName());
        addChild("xs");
        addChild("BIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIGBIG");
        addChild("NormalLength");
    }

    @Override
    protected String defaultValue() {
        return project.getName();
    }

    @Override
    protected String getValueProperty() {
        return Constants.SELECTED_PROJECT_PROPERTY;
    }

    @Override
    protected Resource getPrefixResource() {
        return Resource.PROJECT_SELECT_PREFIX;
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
