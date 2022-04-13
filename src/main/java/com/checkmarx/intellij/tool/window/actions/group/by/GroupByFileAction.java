package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.GroupBy;

public class GroupByFileAction extends GroupByBaseAction {

    public GroupByFileAction() {
        super(Bundle.messagePointer(Resource.FILE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.FILE;
    }
}
