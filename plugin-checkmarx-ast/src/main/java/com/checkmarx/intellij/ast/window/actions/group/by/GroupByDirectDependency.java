package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;

public class GroupByDirectDependency extends GroupByBaseAction {
    public GroupByDirectDependency() {
        super(Bundle.messagePointer(Resource.DIRECT_DEPENDENCY_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.DIRECT_DEPENDENCY;
    }
}
