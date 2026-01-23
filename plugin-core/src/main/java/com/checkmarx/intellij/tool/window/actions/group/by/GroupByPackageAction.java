package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.GroupBy;

public class GroupByPackageAction extends GroupByBaseAction {

    public GroupByPackageAction() {
        super(Bundle.messagePointer(Resource.PACKAGE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.PACKAGE;
    }
}
