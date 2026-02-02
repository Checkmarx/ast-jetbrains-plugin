package com.checkmarx.intellij.common.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.window.GroupBy;

public class GroupByStateAction extends GroupByBaseAction {

    public GroupByStateAction() {
        super(Bundle.messagePointer(Resource.STATE_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.STATE;
    }
}
