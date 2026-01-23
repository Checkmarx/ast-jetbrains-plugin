package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.GroupBy;

public class GroupByStateAction extends GroupByBaseAction {

    public GroupByStateAction() {
        super(Bundle.messagePointer(Resource.STATE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.STATE;
    }
}
