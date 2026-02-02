package com.checkmarx.intellij.common.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.window.GroupBy;

public class GroupByPackageAction extends GroupByBaseAction {

    public GroupByPackageAction() {
        super(Bundle.messagePointer(Resource.PACKAGE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.PACKAGE;
    }
}
