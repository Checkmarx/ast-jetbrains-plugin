package com.checkmarx.intellij.tool.window.actions.group.by;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.results.tree.GroupBy;

@SuppressWarnings("ComponentNotRegistered")
public class GroupBySeverityAction extends GroupByBaseAction {

    public GroupBySeverityAction() {
        super(Bundle.messagePointer(Resource.SEVERITY_ACTION));
    }

    @Override
    protected GroupBy getGroupBy() {
        return GroupBy.SEVERITY;
    }
}
