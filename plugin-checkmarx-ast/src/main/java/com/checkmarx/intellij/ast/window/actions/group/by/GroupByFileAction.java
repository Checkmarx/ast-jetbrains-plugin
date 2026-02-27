package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;

public class GroupByFileAction extends GroupByBaseAction {

    public GroupByFileAction() {
        super(Bundle.messagePointer(Resource.FILE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.FILE;
    }
}
