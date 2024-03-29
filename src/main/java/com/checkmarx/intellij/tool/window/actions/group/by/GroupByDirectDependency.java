package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.GroupBy;

public class GroupByDirectDependency extends GroupByBaseAction  {
    public GroupByDirectDependency() {
        super(Bundle.messagePointer(Resource.DIRECT_DEPENDENCY_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.DIRECT_DEPENDENCY;
    }
}
