package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.window.GroupBy;
import com.checkmarx.intellij.common.window.actions.group.by.GroupByBaseAction;

public class GroupByFileAction extends GroupByBaseAction {

    public GroupByFileAction() {
        super(Bundle.messagePointer(Resource.FILE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.FILE;
    }
}
