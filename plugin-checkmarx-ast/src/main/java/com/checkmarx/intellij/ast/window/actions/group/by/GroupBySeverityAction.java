package com.checkmarx.intellij.ast.window.actions.group.by;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;

public class GroupBySeverityAction extends GroupByBaseAction {

    public GroupBySeverityAction() {
        super(Bundle.messagePointer(Resource.SEVERITY_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.SEVERITY;
    }
}
